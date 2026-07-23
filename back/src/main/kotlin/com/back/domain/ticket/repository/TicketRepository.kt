package com.back.domain.ticket.repository

import com.back.domain.ticket.entity.Ticket
import com.back.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface TicketRepository : JpaRepository<Ticket, Long> {

    fun findByTicketIdAndUser_UserId(ticketId: Long, userId: Long): Optional<Ticket>

    @Query("""
        SELECT t FROM Ticket t
        JOIN FETCH t.schedule s
        JOIN FETCH s.concert
        JOIN FETCH t.scheduleSeat
        WHERE t.user = :user
    """)
    fun findAllByUserWithConcert(@Param("user") user: User): List<Ticket>

    fun countByUser_UserIdAndSchedule_ScheduleIdAndIsValidTrue(userId: Long, scheduleId: Long): Long

    @Query("""
        SELECT t FROM Ticket t
        JOIN FETCH t.schedule s
        JOIN FETCH s.concert
        JOIN FETCH s.venue
        JOIN FETCH t.scheduleSeat
        WHERE t.qrToken = :qrToken
    """)
    fun findByQrTokenWithDetails(@Param("qrToken") qrToken: String): Optional<Ticket>
}
