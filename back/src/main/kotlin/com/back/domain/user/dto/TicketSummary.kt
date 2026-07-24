package com.back.domain.user.dto

import com.back.domain.ticket.entity.Ticket

data class TicketSummary(
    val ticketId: Long,
    val ticketNumber: String,
    val qrToken: String?,
    val seatNumber: String,
    val gradeName: String,
    val ticketPrice: Int,
    val isValid: Boolean,
    val createdAt: String
) {
    companion object {
        fun from(ticket: Ticket): TicketSummary = TicketSummary(
            ticketId = checkNotNull(ticket.ticketId) { "Ticket ID null" },
            ticketNumber = ticket.ticketNumber,
            qrToken = ticket.qrToken,
            seatNumber = ticket.scheduleSeat.seatNumber,
            gradeName = ticket.scheduleSeat.gradeName,
            ticketPrice = ticket.ticketPrice,
            isValid = ticket.isValid,
            createdAt = checkNotNull(ticket.createDate) { "Create date null" }.toLocalDate().toString()
        )
    }
}
