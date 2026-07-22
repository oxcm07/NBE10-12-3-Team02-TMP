package com.back.domain.concert.listener;

import com.back.domain.concert.event.SeatExpiredEvent;
import com.back.domain.concert.service.SeatOccupyManager;
import com.back.domain.schedule.entity.ScheduleSeat;
import com.back.domain.schedule.entity.SeatStatus;
import com.back.domain.schedule.repository.ScheduleSeatRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Redisson Delayed Queue 기반 좌석 선점 만료 복구 핸들러.
 *
 * <p>TTL이 경과한 좌석 선점 메시지를 큐에서 꺼내 DB 좌석 상태를 {@code AVAILABLE}로 복구합니다.
 * 이후 SSE 브로드캐스트를 위해 {@link SeatExpiredEvent}를 발행합니다.
 *
 * <h2>흐름</h2>
 * <ol>
 *   <li>애플리케이션 시작 시 백그라운드 스레드에서 {@code RBlockingQueue.take()} 대기</li>
 *   <li>TTL 경과 → 메시지({@code "concertId:scheduleId:seatNumber"}) 수신</li>
 *   <li>DB에서 해당 좌석이 아직 {@code HOLD} 상태인 경우 {@code AVAILABLE}로 복구</li>
 *   <li>Redis Hash 및 ZSET 인덱스 정리</li>
 *   <li>SSE 이벤트 발행으로 실시간 좌석 상태 변경 클라이언트에 Push</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatHoldExpiredHandler {

    public static final String DELAYED_QUEUE_KEY = "seat:hold:expired:queue";

    private final RedissonClient redissonClient;
    private final ScheduleSeatRepository scheduleSeatRepository;
    private final SeatOccupyManager seatOccupyManager;
    private final ApplicationEventPublisher eventPublisher;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "seat-hold-expired-handler");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running = true;

    @PostConstruct
    public void startListening() {
        executor.submit(this::listen);
        log.info("SeatHoldExpiredHandler 시작: Delayed Queue [{}] 감시 중", DELAYED_QUEUE_KEY);
    }

    @PreDestroy
    public void stopListening() {
        running = false;
        executor.shutdownNow();
        log.info("SeatHoldExpiredHandler 종료");
    }

    private void listen() {
        RBlockingQueue<String> blockingQueue = redissonClient.getBlockingQueue(DELAYED_QUEUE_KEY);

        while (running) {
            try {
                // 최대 2초 대기 후 null 반환 (running 체크 가능하도록)
                String message = blockingQueue.poll(2, TimeUnit.SECONDS);
                if (message != null) {
                    processExpiredSeat(message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Delayed Queue 메시지 처리 중 오류: {}", e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void processExpiredSeat(String message) {
        String[] parts = message.split(":");
        if (parts.length != 3) {
            log.warn("잘못된 Delayed Queue 메시지 형식: {}", message);
            return;
        }

        Long concertId = Long.parseLong(parts[0]);
        Long scheduleId = Long.parseLong(parts[1]);
        String seatNumber = parts[2];

        log.debug("좌석 선점 만료 처리: concertId={}, scheduleId={}, seat={}", concertId, scheduleId, seatNumber);

        // DB 좌석이 아직 HOLD 상태인 경우에만 AVAILABLE로 복구
        Optional<ScheduleSeat> optSeat = scheduleSeatRepository
                .findWithLockByScheduleIdAndSeatNumber(scheduleId, seatNumber);

        optSeat.ifPresent(seat -> {
            if (seat.getSeatStatus() == SeatStatus.HOLD) {
                seat.updateSeatStatus(SeatStatus.AVAILABLE);
                log.info("좌석 복구 완료 (HOLD → AVAILABLE): scheduleId={}, seat={}", scheduleId, seatNumber);

                // Redis Hash 및 ZSET 인덱스 정리
                String redisKey = SeatOccupyManager.generateSeatOccupyKey(concertId, scheduleId, seatNumber);
                String indexKey = SeatOccupyManager.generateSeatOccupyIndexKey(concertId, scheduleId);
                seatOccupyManager.cleanupRedis(redisKey, indexKey, seatNumber);

                // SSE 브로드캐스트를 위한 이벤트 발행
                eventPublisher.publishEvent(new SeatExpiredEvent(concertId, scheduleId, seatNumber));
            }
        });
    }
}
