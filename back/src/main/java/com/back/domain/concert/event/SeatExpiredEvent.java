package com.back.domain.concert.event;

/**
 * 좌석 선점 만료 이벤트.
 *
 * <p>Redisson Delayed Queue에서 TTL 경과 후 꺼낸 메시지를 처리하는 과정에서
 * DB 복구 완료 후 SSE 브로드캐스트를 위해 발행됩니다.
 *
 * @param concertId  공연 ID
 * @param scheduleId 회차 ID
 * @param seatNumber 좌석 번호
 */
public record SeatExpiredEvent(
        Long concertId,
        Long scheduleId,
        String seatNumber
) {
}
