package com.back.domain.schedule.repository

import com.back.domain.schedule.entity.ScheduleSeat
import com.back.domain.schedule.entity.SeatStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ScheduleSeatRepository : JpaRepository<ScheduleSeat, Long> {

    fun findByScheduleScheduleId(scheduleId: Long): List<ScheduleSeat>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select ss
        from ScheduleSeat ss
        where ss.schedule.scheduleId = :scheduleId
          and ss.seatNumber = :seatNumber
    """)
    fun findWithLockByScheduleIdAndSeatNumber(
        @Param("scheduleId") scheduleId: Long,
        @Param("seatNumber") seatNumber: String
    ): ScheduleSeat?

    fun countBySchedule_ScheduleIdAndSeatStatus(scheduleId: Long, seatStatus: SeatStatus): Long

    fun countBySchedule_ScheduleIdAndSeatStatusIn(scheduleId: Long, seatStatuses: Collection<SeatStatus>): Long
}
