package com.back.domain.concert.listener;

import com.back.domain.concert.event.SeatOccupiedEvent;
import com.back.domain.concert.service.SeatOccupyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.TimeUnit;

/**
 * 좌석 선점 완료 이벤트 리스너.
 *
 * <p>DB 트랜잭션 커밋 완료 후({@code AFTER_COMMIT}) 실행됩니다.
 * Redisson Delayed Queue에 좌석 만료 메시지를 등록하여,
 * TTL 경과 후 {@link SeatHoldExpiredHandler}가 DB를 복구할 수 있도록 합니다.
 *
 * <p>이 방식은 "DB 커밋 성공 → Delayed Queue 등록"을 보장하므로,
 * 결제 전 트랜잭션 롤백 시에는 Delayed Queue 메시지가 등록되지 않습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatOccupiedEventListener {

    private final RedissonClient redissonClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatOccupied(SeatOccupiedEvent event) {
        String message = buildMessage(event.concertId(), event.scheduleId(), event.seatNumber());

        try {
            RBlockingQueue<String> blockingQueue = redissonClient.getBlockingQueue(
                    SeatHoldExpiredHandler.DELAYED_QUEUE_KEY);
            RDelayedQueue<String> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);

            delayedQueue.offer(message, event.ttlSeconds(), TimeUnit.SECONDS);

            log.debug("Delayed Queue 등록 완료: {}, TTL={}s", message, event.ttlSeconds());
        } catch (Exception e) {
            // Delayed Queue 등록 실패는 치명적이지 않습니다.
            // Redis TTL 자체가 만료되면 SeatHoldExpiredHandler가 별도로 처리합니다.
            log.warn("Delayed Queue 등록 실패 (좌석 TTL 만료 시 자동 복구됨): {}", message, e);
        }
    }

    public static String buildMessage(Long concertId, Long scheduleId, String seatNumber) {
        return concertId + ":" + scheduleId + ":" + seatNumber;
    }
}
