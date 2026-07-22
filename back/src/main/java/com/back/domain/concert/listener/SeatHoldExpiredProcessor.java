package com.back.domain.concert.listener;

import com.back.domain.concert.event.SeatExpiredEvent;
import com.back.domain.concert.service.SeatOccupyManager;
import com.back.domain.schedule.entity.ScheduleSeat;
import com.back.domain.schedule.entity.SeatStatus;
import com.back.domain.schedule.repository.ScheduleSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/**
 * 좌석 선점 만료 메시지의 트랜잭션 처리를 담당하는 서비스.
 *
 * <p>Spring AOP 프록시를 통해 호출되어야 {@code @Transactional}이 적용됩니다.
 * {@link SeatHoldExpiredHandler}의 백그라운드 스레드가 이 빈을 주입받아 호출합니다.
 *
 * <h2>트랜잭션 처리 흐름</h2>
 * <ol>
 *   <li>{@link #processExpiredSeat}: DB HOLD → AVAILABLE 업데이트 + {@link SeatExpiredEvent} 발행</li>
 *   <li>{@link #onSeatExpiredCleanupRedis}: {@code AFTER_COMMIT} 후 Redis Hash/ZSET 정리</li>
 * </ol>
 *
 * <p>Redis cleanup을 {@code AFTER_COMMIT} 이후에 수행하여,
 * DB 롤백 시 Redis 키가 불필요하게 삭제되지 않도록 합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatHoldExpiredProcessor {

    private final ScheduleSeatRepository scheduleSeatRepository;
    private final SeatOccupyManager seatOccupyManager;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * DB 좌석 상태를 HOLD → AVAILABLE로 복구하고 만료 이벤트를 발행합니다.
     *
     * <p>Spring AOP 프록시를 통해 호출되므로 {@code @Transactional}이 정상 적용됩니다.
     * DB 커밋 후 {@link #onSeatExpiredCleanupRedis}가 Redis를 정리합니다.
     *
     * @param concertId  공연 ID
     * @param scheduleId 회차 ID
     * @param seatNumber 좌석 번호
     */
    @Transactional
    public void processExpiredSeat(Long concertId, Long scheduleId, String seatNumber) {
        log.debug("좌석 선점 만료 처리: concertId={}, scheduleId={}, seat={}", concertId, scheduleId, seatNumber);

        Optional<ScheduleSeat> optSeat = scheduleSeatRepository
                .findWithLockByScheduleIdAndSeatNumber(scheduleId, seatNumber);

        optSeat.ifPresent(seat -> {
            if (seat.getSeatStatus() == SeatStatus.HOLD) {
                seat.updateSeatStatus(SeatStatus.AVAILABLE);
                log.info("좌석 복구 완료 (HOLD → AVAILABLE): scheduleId={}, seat={}", scheduleId, seatNumber);

                // DB 커밋 후 Redis 정리 및 SSE 브로드캐스트를 위한 이벤트 발행
                eventPublisher.publishEvent(new SeatExpiredEvent(concertId, scheduleId, seatNumber));
            } else {
                log.debug("좌석이 HOLD 상태가 아님 (이미 처리됨): scheduleId={}, seat={}, status={}",
                        scheduleId, seatNumber, seat.getSeatStatus());
            }
        });
    }

    /**
     * DB 커밋 완료 후 Redis Hash 및 ZSET 인덱스를 정리합니다.
     *
     * <p>트랜잭션 커밋 실패 시에는 실행되지 않아, 불필요한 Redis 키 삭제를 방지합니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatExpiredCleanupRedis(SeatExpiredEvent event) {
        String redisKey = SeatOccupyManager.generateSeatOccupyKey(
                event.concertId(), event.scheduleId(), event.seatNumber());
        String indexKey = SeatOccupyManager.generateSeatOccupyIndexKey(
                event.concertId(), event.scheduleId());

        try {
            seatOccupyManager.cleanupRedis(redisKey, indexKey, event.seatNumber());
            log.debug("만료 좌석 Redis 정리 완료: {}", redisKey);
        } catch (Exception e) {
            log.warn("만료 좌석 Redis 정리 실패 (무시됨): {}", redisKey, e);
        }
    }
}
