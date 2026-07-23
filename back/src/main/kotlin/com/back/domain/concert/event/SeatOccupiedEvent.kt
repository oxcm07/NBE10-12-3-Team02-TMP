package com.back.domain.concert.event

data class SeatOccupiedEvent(
    val concertId: Long,
    val scheduleId: Long,
    val seatNumber: String,
    val ttlSeconds: Long
)
