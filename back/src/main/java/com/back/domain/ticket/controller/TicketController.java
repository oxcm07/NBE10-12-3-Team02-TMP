package com.back.domain.ticket.controller;

import com.back.domain.ticket.dto.PaymentTicketRequest;
import com.back.domain.ticket.dto.PaymentTicketResponse;
import com.back.domain.ticket.dto.TicketVerifyResponse;
import com.back.domain.ticket.service.TicketService;
import com.back.global.annotation.ApiV1;
import com.back.global.requestcontext.RequestContext;
import com.back.global.rsData.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ApiV1
@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
@Tag(name = "Ticket", description = "Ticket API")
public class TicketController {

    private final TicketService ticketService;
    private final RequestContext requestContext;

    @PostMapping("/reserve/schedule/{scheduleId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "결제 및 티켓 생성", description = "결제 및 티켓 생성 API")
    public RsData<List<PaymentTicketResponse>> createTicket(
            @PathVariable Long scheduleId,
            @RequestBody @Valid PaymentTicketRequest request
    ) {
        List<PaymentTicketResponse> response = ticketService.createTicket(requestContext.getActor().getId(), scheduleId, request);
        return new RsData<>(
                "201-1",
                "결제 및 티켓 생성 성공",
                response
        );
    }

    @PatchMapping("/cancel/{ticketId}")
    @Operation(summary = "결제 취소", description = "결제 취소 API")
    public RsData<Void> cancelTicket(
            @PathVariable(value = "ticketId") Long ticketId
    ) {
        ticketService.cancelTicket(requestContext.getActor().getId(), ticketId);
        return new RsData<>(
                "200-1",
                "티켓 취소 성공",
                null
        );
    }

    @GetMapping("/verify/{qrToken}")
    @Operation(summary = "티켓 QR 검증", description = "QR 토큰으로 티켓 유효성을 검증합니다 (인증 불필요)")
    public RsData<TicketVerifyResponse> verifyTicket(@PathVariable String qrToken) {
        return new RsData<>(
                "200-1",
                "티켓 검증 성공",
                ticketService.verifyTicket(qrToken)
        );
    }
}
