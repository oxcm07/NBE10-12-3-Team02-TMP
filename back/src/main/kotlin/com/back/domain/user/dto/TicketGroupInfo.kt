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
            require(tickets.isNotEmpty()) { "Tickets list must not be empty" }
            val first = tickets.first()
            val schedule = first.schedule
            val concert = schedule.concert

            return TicketGroupInfo(
                scheduleId = checkNotNull(schedule.scheduleId) { "Schedule ID null" },
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
