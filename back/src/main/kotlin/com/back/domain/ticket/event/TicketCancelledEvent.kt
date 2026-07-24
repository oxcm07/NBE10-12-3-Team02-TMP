package com.back.domain.ticket.event

data class TicketCancelledEvent(
    val concertId: Long,
    val scheduleId: Long,
    val userId: Long
)
