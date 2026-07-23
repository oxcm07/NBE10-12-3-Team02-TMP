package com.back.domain.concert.event

data class SeatExpiredEvent(
    val concertId: Long,
    val scheduleId: Long,
    val seatNumber: String
)
