package com.back.domain.concert.sse

import com.back.domain.concert.event.SeatExpiredEvent
import com.back.domain.schedule.entity.SeatStatus
import com.back.domain.ticket.event.PaymentCompletedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Async
@Component
class SeatStatusSseBroadcaster(
    private val registry: SeatStatusSseEmitterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onSeatExpired(event: SeatExpiredEvent) {
        log.debug("SSE 브로드캐스트 (만료 복구): scheduleId={}, seat={}", event.scheduleId, event.seatNumber)
        registry.broadcast(event.scheduleId, event.seatNumber, SeatStatus.AVAILABLE.name)
    }

    @EventListener
    fun onPaymentCompleted(event: PaymentCompletedEvent) {
        log.debug("SSE 브로드캐스트 (결제 완료): scheduleId={}", event.scheduleId)
    }
}
