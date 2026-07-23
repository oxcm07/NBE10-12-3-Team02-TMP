package com.back.domain.schedule.repository

import com.back.domain.schedule.entity.Schedule
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface ScheduleRepository : JpaRepository<Schedule, Long> {

    fun findFirstByConcertConcertId(concertId: Long): Optional<Schedule>

    fun findByConcertConcertId(concertId: Long): List<Schedule>

    fun findByScheduleIdAndConcert_ConcertId(scheduleId: Long, concertId: Long): Optional<Schedule>

    @Query("SELECT s FROM Schedule s JOIN FETCH s.venue WHERE s.concert.concertId IN :concertIds")
    fun findAllWithVenueByConcertIds(@Param("concertIds") concertIds: List<Long>): List<Schedule>

    @Query("SELECT s FROM Schedule s JOIN FETCH s.venue WHERE s.concert.concertId = :concertId ORDER BY s.scheduleId")
    fun findWithVenueByConcertId(@Param("concertId") concertId: Long): List<Schedule>
}
