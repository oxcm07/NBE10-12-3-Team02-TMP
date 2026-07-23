package com.back.domain.user.dto

import com.back.domain.ticket.entity.Ticket

data class TicketGroupInfo(
    val scheduleId: Long,
    val concertName: String,
    val urlPoster: String?,
    val startDate: String,
    val endDate: String,
    val round: Int,
    val totalPrice: Int,
    val tickets: List<TicketSummary>
) {
    companion object {
        fun from(tickets: List<Ticket>): TicketGroupInfo {
            val first = tickets[0]
            val schedule = first.schedule
            val concert = schedule.concert

            return TicketGroupInfo(
                scheduleId = schedule.scheduleId ?: throw IllegalArgumentException("Schedule ID null"),
                concertName = concert.concertName,
                urlPoster = concert.urlPoster,
                startDate = concert.startDate.toLocalDate().toString(),
                endDate = concert.endDate.toLocalDate().toString(),
                round = schedule.round,
                totalPrice = tickets.sumOf { it.ticketPrice },
                tickets = tickets.map { TicketSummary.from(it) }
            )
        }
    }
}
