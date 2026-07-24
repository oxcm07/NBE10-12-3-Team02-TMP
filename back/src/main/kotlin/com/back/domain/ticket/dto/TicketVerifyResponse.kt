package com.back.domain.ticket.dto

import com.back.domain.ticket.entity.Ticket
import java.time.LocalDateTime

data class TicketVerifyResponse(
    val concertName: String,
    val venueName: String,
    val scheduleDate: LocalDateTime,
    val seatNumber: String,
    val isValid: Boolean
) {
    companion object {
        fun from(ticket: Ticket): TicketVerifyResponse {
            return TicketVerifyResponse(
                concertName = ticket.schedule.concert.concertName,
                venueName = ticket.schedule.venue.venueName,
                scheduleDate = ticket.schedule.scheduleDate,
                seatNumber = ticket.scheduleSeat.seatNumber,
                isValid = ticket.isValid
            )
        }
    }
}
