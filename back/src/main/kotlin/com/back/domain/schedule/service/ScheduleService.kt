package com.back.domain.schedule.service

import com.back.domain.concert.repository.ConcertRepository
import com.back.domain.schedule.dto.ShowScheduleListResponse
import com.back.domain.schedule.dto.ShowScheduleResponse
import com.back.domain.schedule.entity.SeatStatus
import com.back.domain.schedule.repository.ScheduleRepository
import com.back.domain.schedule.repository.ScheduleSeatRepository
import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ScheduleService(
    private val scheduleRepository: ScheduleRepository,
    private val concertRepository: ConcertRepository,
    private val scheduleSeatRepository: ScheduleSeatRepository
) {
    fun showSchedule(concertId: Long, scheduleId: Long): ShowScheduleResponse {
        val schedule = scheduleRepository
            .findByScheduleIdAndConcert_ConcertId(scheduleId, concertId)
            .orElseThrow { ServiceException(ErrorCode.CONCERT_NOT_FOUND_OR_MISMATCH) }

        val remainingSeats = scheduleSeatRepository
            .countBySchedule_ScheduleIdAndSeatStatus(scheduleId, SeatStatus.AVAILABLE)

        return ShowScheduleResponse.of(schedule, remainingSeats)
    }

    fun showScheduleList(concertId: Long): List<ShowScheduleListResponse> {
        if (!concertRepository.existsById(concertId)) {
            throw ServiceException(ErrorCode.CONCERT_NOT_FOUND)
        }

        val schedules = scheduleRepository.findByConcertConcertId(concertId)

        if (schedules.isEmpty()) {
            throw ServiceException(ErrorCode.CONCERT_SCHEDULE_EMPTY)
        }

        return schedules.map { schedule ->
            val remainingSeats = scheduleSeatRepository
                .countBySchedule_ScheduleIdAndSeatStatus(schedule.scheduleId!!, SeatStatus.AVAILABLE)
            ShowScheduleListResponse.of(schedule, remainingSeats)
        }
    }
}
