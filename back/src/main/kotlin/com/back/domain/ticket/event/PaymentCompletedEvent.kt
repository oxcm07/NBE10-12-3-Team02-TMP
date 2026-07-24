package com.back.domain.ticket.event

data class PaymentCompletedEvent(
    val concertId: Long,
    val scheduleId: Long,
    val userId: Long
)
