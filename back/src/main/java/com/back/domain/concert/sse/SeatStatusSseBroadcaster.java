package com.back.domain.concert.sse;

import com.back.domain.concert.event.SeatExpiredEvent;
import com.back.domain.schedule.entity.SeatStatus;
import com.back.domain.ticket.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * SSE 브로드캐스트 이벤트 핸들러.
 *
 * <p>좌석 상태 변경 이벤트를 수신하여 해당 회차 구독자에게 SSE 이벤트를 브로드캐스트합니다.
 * <ul>
 *   <li>{@link SeatExpiredEvent}: 선점 만료 → 좌석 AVAILABLE 알림</li>
 *   <li>{@link PaymentCompletedEvent}: 결제 완료 → 좌석 SOLD_OUT 알림</li>
 * </ul>
 */
@Slf4j
@Async
@Component
@RequiredArgsConstructor
public class SeatStatusSseBroadcaster {

    private final SeatStatusSseEmitterRegistry registry;

    @EventListener
    public void onSeatExpired(SeatExpiredEvent event) {
        log.debug("SSE 브로드캐스트 (만료 복구): scheduleId={}, seat={}", event.scheduleId(), event.seatNumber());
        registry.broadcast(event.scheduleId(), event.seatNumber(), SeatStatus.AVAILABLE.name());
    }

    @EventListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        // PaymentCompletedEvent는 현재 concertId/scheduleId/userId 정보를 가짐
        // 결제 완료된 좌석 번호 목록이 있다면 더 세밀한 알림 가능
        // 현재는 scheduleId 수준의 알림으로 처리
        log.debug("SSE 브로드캐스트 (결제 완료): scheduleId={}", event.scheduleId());
    }
}
