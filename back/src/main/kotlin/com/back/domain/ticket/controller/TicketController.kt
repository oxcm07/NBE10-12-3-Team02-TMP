package com.back.domain.ticket.controller

import com.back.domain.ticket.dto.PaymentTicketRequest
import com.back.domain.ticket.dto.PaymentTicketResponse
import com.back.domain.ticket.dto.TicketVerifyResponse
import com.back.domain.ticket.service.TicketService
import com.back.global.annotation.ApiV1
import com.back.global.requestcontext.RequestContext
import com.back.global.rsData.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@ApiV1
@RestController
@RequestMapping("/tickets")
@Tag(name = "Ticket", description = "Ticket API")
class TicketController(
    private val ticketService: TicketService,
    private val requestContext: RequestContext
) {

    @PostMapping("/reserve/schedule/{scheduleId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "결제 및 티켓 생성", description = "결제 및 티켓 생성 API")
    fun createTicket(
        @PathVariable scheduleId: Long,
        @RequestBody @Valid request: PaymentTicketRequest
    ): RsData<List<PaymentTicketResponse>> {
        val actor = requestContext.actor ?: throw IllegalStateException("Actor must not be null")
        val response = ticketService.createTicket(actor.id, scheduleId, request)
        return RsData(
            "201-1",
            "결제 및 티켓 생성 성공",
            response
        )
    }

    @PatchMapping("/cancel/{ticketId}")
    @Operation(summary = "결제 취소", description = "결제 취소 API")
    fun cancelTicket(
        @PathVariable(value = "ticketId") ticketId: Long
    ): RsData<Void> {
        val actor = requestContext.actor ?: throw IllegalStateException("Actor must not be null")
        ticketService.cancelTicket(actor.id, ticketId)
        return RsData(
            "200-1",
            "티켓 취소 성공",
            null
        )
    }

    @GetMapping("/verify/{qrToken}")
    @Operation(summary = "티켓 QR 검증", description = "QR 토큰으로 티켓 유효성을 검증합니다 (인증 불필요)")
    fun verifyTicket(@PathVariable qrToken: String): RsData<TicketVerifyResponse> {
        return RsData(
            "200-1",
            "티켓 검증 성공",
            ticketService.verifyTicket(qrToken)
        )
    }
}
