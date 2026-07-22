package com.back.domain.concert.listener;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Redisson Delayed Queue 기반 좌석 선점 만료 복구 핸들러.
 *
 * <p>TTL이 경과한 좌석 선점 메시지를 큐에서 꺼내 {@link SeatHoldExpiredProcessor}에 위임합니다.
 *
 * <h2>흐름</h2>
 * <ol>
 *   <li>애플리케이션 시작 시 백그라운드 스레드에서 {@code RBlockingQueue.poll()} 대기</li>
 *   <li>TTL 경과 → 메시지({@code "concertId:scheduleId:seatNumber"}) 수신</li>
 *   <li>{@link SeatHoldExpiredProcessor#processExpiredSeat}에 위임 (Spring 프록시를 통한 @Transactional 보장)</li>
 * </ol>
 *
 * <h2>트랜잭션 설계</h2>
 * <p>이 클래스는 트랜잭션 처리를 직접 수행하지 않습니다.
 * 모든 DB 작업은 {@link SeatHoldExpiredProcessor}를 통해 Spring AOP 프록시 하에서 수행됩니다.
 * 이를 통해 self-invocation에 의한 {@code @Transactional} 무효화 문제를 방지합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "seat.hold.handler.enabled", havingValue = "true", matchIfMissing = true)
public class SeatHoldExpiredHandler {

    public static final String DELAYED_QUEUE_KEY = "seat:hold:expired:queue";

    private final RedissonClient redissonClient;
    private final SeatHoldExpiredProcessor seatHoldExpiredProcessor;

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
        while (running) {
            try {
                RBlockingQueue<String> blockingQueue = redissonClient.getBlockingQueue(DELAYED_QUEUE_KEY);
                // 최대 2초 대기 후 null 반환 (running 체크 가능하도록)
                String message = blockingQueue.poll(2, TimeUnit.SECONDS);
                if (message != null) {
                    handleMessage(message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Delayed Queue 메시지 처리 중 오류: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 메시지를 파싱하여 {@link SeatHoldExpiredProcessor}에 위임합니다.
     *
     * <p>Spring Bean({@link SeatHoldExpiredProcessor})을 외부에서 호출하므로
     * Spring AOP 프록시가 정상 동작하여 {@code @Transactional}이 적용됩니다.
     */
    private void handleMessage(String message) {
        String[] parts = message.split(":");
        if (parts.length != 3) {
            log.warn("잘못된 Delayed Queue 메시지 형식: {}", message);
            return;
        }

        try {
            Long concertId = Long.parseLong(parts[0]);
            Long scheduleId = Long.parseLong(parts[1]);
            String seatNumber = parts[2];

            // Spring AOP 프록시를 통해 호출 → @Transactional 정상 적용
            seatHoldExpiredProcessor.processExpiredSeat(concertId, scheduleId, seatNumber);
        } catch (NumberFormatException e) {
            log.warn("Delayed Queue 메시지 파싱 오류: {}", message, e);
        } catch (Exception e) {
            log.error("좌석 만료 처리 실패: {}", message, e);
        }
    }
}
