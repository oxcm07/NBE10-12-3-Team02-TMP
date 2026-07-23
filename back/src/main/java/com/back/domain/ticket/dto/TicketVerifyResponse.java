package com.back.domain.ticket.dto;

import com.back.domain.ticket.entity.Ticket;

import java.time.LocalDateTime;

public record TicketVerifyResponse(
        String concertName,
        String venueName,
        LocalDateTime scheduleDate,
        String seatNumber,
        boolean isValid
) {
    public static TicketVerifyResponse from(Ticket ticket) {
        return new TicketVerifyResponse(
                ticket.getSchedule().getConcert().getConcertName(),
                ticket.getSchedule().getVenue().getVenueName(),
                ticket.getSchedule().getScheduleDate(),
                ticket.getScheduleSeat().getSeatNumber(),
                ticket.isValid()
        );
    }
}
