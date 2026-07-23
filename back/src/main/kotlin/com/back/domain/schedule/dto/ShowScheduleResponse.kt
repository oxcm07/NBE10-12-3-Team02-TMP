package com.back.domain.schedule.dto

import com.back.domain.schedule.entity.Schedule
import java.time.LocalDateTime

data class ShowScheduleResponse(
    val concertId: Long?,
    val scheduleId: Long?,
    val round: Int,
    val scheduleDate: LocalDateTime,
    val remainingSeats: Long
) {
    companion object {
        @JvmStatic
        fun of(schedule: Schedule, remainingSeats: Long): ShowScheduleResponse =
            ShowScheduleResponse(
                concertId = schedule.concert.concertId,
                scheduleId = schedule.scheduleId,
                round = schedule.round,
                scheduleDate = schedule.scheduleDate,
                remainingSeats = remainingSeats
            )
    }
}
