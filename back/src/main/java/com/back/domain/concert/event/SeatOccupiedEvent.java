package com.back.domain.concert.event;

/**
 * 좌석 선점 완료 이벤트.
 *
 * <p>DB 트랜잭션 커밋 후 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}에서
 * Redisson Delayed Queue에 만료 복구 메시지를 등록할 때 사용됩니다.
 *
 * @param concertId   공연 ID
 * @param scheduleId  회차 ID
 * @param seatNumber  좌석 번호
 * @param ttlSeconds  선점 유지 시간 (초)
 */
public record SeatOccupiedEvent(
        Long concertId,
        Long scheduleId,
        String seatNumber,
        long ttlSeconds
) {
}
