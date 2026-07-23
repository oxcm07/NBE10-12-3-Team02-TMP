package com.back.domain.ticket.repository;

import com.back.domain.ticket.entity.Ticket;
import com.back.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByTicketIdAndUser_UserId(Long ticketId, Long userId);

    @Query("""
        SELECT t FROM Ticket t
        JOIN FETCH t.schedule s
        JOIN FETCH s.concert
        JOIN FETCH t.scheduleSeat
        WHERE t.user = :user
    """)
    List<Ticket> findAllByUserWithConcert(@Param("user") User user);

    long countByUser_UserIdAndSchedule_ScheduleIdAndIsValidTrue(Long userId, Long scheduleId);

    @Query("""
        SELECT t FROM Ticket t
        JOIN FETCH t.schedule s
        JOIN FETCH s.concert
        JOIN FETCH s.venue
        JOIN FETCH t.scheduleSeat
        WHERE t.qrToken = :qrToken
    """)
    Optional<Ticket> findByQrTokenWithDetails(@Param("qrToken") String qrToken);
}