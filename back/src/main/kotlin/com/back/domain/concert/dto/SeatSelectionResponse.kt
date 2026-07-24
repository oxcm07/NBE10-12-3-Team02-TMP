package com.back.domain.concert.dto

import com.back.domain.schedule.entity.ScheduleSeat
import com.back.domain.schedule.entity.SeatStatus

data class SeatSelectionResponse(
    val concertId: Long,
    val scheduleId: Long,
    val prices: Map<String, Int>,
    val seats: List<SeatDetailResponse>
) {
    companion object {
        fun of(
            concertId: Long,
            scheduleId: Long,
            prices: Map<String, Int>,
            seats: List<SeatDetailResponse>
        ): SeatSelectionResponse = SeatSelectionResponse(concertId, scheduleId, prices, seats)
    }

    data class SeatDetailResponse(
        val seatNumber: String,
        val seatStatus: SeatStatus,
        val gradeName: String
    ) {
        companion object {
            fun from(scheduleSeat: ScheduleSeat): SeatDetailResponse = SeatDetailResponse(
                scheduleSeat.seatNumber,
                scheduleSeat.seatStatus,
                scheduleSeat.gradeName
            )
        }
    }
}
