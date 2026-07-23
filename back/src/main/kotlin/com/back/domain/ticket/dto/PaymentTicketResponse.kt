package com.back.domain.ticket.dto

import com.back.domain.schedule.entity.Schedule
import com.back.domain.schedule.entity.ScheduleSeat
import com.back.domain.schedule.entity.SeatStatus
import com.back.domain.ticket.entity.Ticket
import java.time.LocalDateTime

data class PaymentTicketResponse(
    val ticketNumber: String,
    val urlPoster: String?,
    val concertName: String,
    val seatNumber: String,
    val scheduleDate: LocalDateTime,
    val seatStatus: SeatStatus,
    val isValid: Boolean
) {
    companion object {
        fun from(scheduleSeat: ScheduleSeat, schedule: Schedule, ticket: Ticket): PaymentTicketResponse {
            return PaymentTicketResponse(
                ticketNumber = ticket.ticketNumber,
                urlPoster = schedule.concert.urlPoster,
                concertName = schedule.concert.concertName,
                seatNumber = scheduleSeat.seatNumber,
                scheduleDate = schedule.scheduleDate,
                seatStatus = scheduleSeat.seatStatus,
                isValid = ticket.isValid
            )
        }
    }
}
