package com.back.domain.user.dto

import com.back.domain.ticket.entity.Ticket
import com.fasterxml.jackson.annotation.JsonProperty

data class TicketInfo(
    val ticketId: Long,
    val urlPoster: String?,
    val concertName: String,
    val startDate: String,
    val endDate: String,
    @get:JsonProperty("isValid") val isValid: Boolean,
    val ticketPrice: Int,
    val createdAt: String,
    val ticketNumber: String,
    val seatNumber: String,
    val gradeName: String,
    val round: Int
) {
    companion object {
        fun from(ticket: Ticket): TicketInfo {
            val ticketId = ticket.ticketId ?: throw IllegalArgumentException("Ticket ID null")
            val schedule = ticket.schedule
            val concert = schedule.concert
            val createDate = ticket.createDate ?: throw IllegalArgumentException("Create date null")

            return TicketInfo(
                ticketId = ticketId,
                urlPoster = concert.urlPoster,
                concertName = concert.concertName,
                startDate = concert.startDate.toLocalDate().toString(),
                endDate = concert.endDate.toLocalDate().toString(),
                isValid = ticket.isValid,
                ticketPrice = ticket.ticketPrice,
                createdAt = createDate.toLocalDate().toString(),
                ticketNumber = ticket.ticketNumber,
                seatNumber = ticket.scheduleSeat.seatNumber,
                gradeName = ticket.scheduleSeat.gradeName,
                round = schedule.round
            )
        }
    }
}
