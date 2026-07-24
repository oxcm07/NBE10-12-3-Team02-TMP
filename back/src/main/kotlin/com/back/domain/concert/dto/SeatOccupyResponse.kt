package com.back.domain.concert.dto

import com.back.domain.schedule.entity.SeatStatus

data class SeatOccupyResponse(
    val occupyToken: String,
    val expireInSeconds: Long,
    val seatStatus: SeatStatus = SeatStatus.HOLD
) {
    companion object {
        fun of(occupyToken: String, expireInSeconds: Long): SeatOccupyResponse =
            SeatOccupyResponse(occupyToken, expireInSeconds, SeatStatus.HOLD)
    }
}
