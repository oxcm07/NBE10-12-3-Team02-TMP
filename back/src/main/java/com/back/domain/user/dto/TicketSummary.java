package com.back.domain.user.dto;

import com.back.domain.ticket.entity.Ticket;

public record TicketSummary(
        Long ticketId,
        String ticketNumber,
        String qrToken,
        String seatNumber,
        String gradeName,
        int ticketPrice,
        boolean isValid,
        String createdAt
) {
    public static TicketSummary from(Ticket ticket) {
        return new TicketSummary(
                ticket.getTicketId(),
                ticket.getTicketNumber(),
                ticket.getQrToken(),
                ticket.getScheduleSeat().getSeatNumber(),
                ticket.getScheduleSeat().getGradeName(),
                ticket.getTicketPrice(),
                ticket.isValid(),
                ticket.getCreateDate().toLocalDate().toString()
        );
    }
}