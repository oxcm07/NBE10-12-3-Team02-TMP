package com.back.domain.ticket.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

data class PaymentTicketRequest(
    @field:Schema(description = "공연 ID", example = "1")
    @field:NotNull(message = "공연 ID는 필수입니다.")
    val concertId: Long,

    @field:Schema(description = "예매할 좌석 번호", example = "A-1")
    val seatHolds: List<SeatHoldInfo>
)
