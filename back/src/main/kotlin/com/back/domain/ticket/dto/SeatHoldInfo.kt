package com.back.domain.ticket.dto

import jakarta.validation.constraints.NotBlank

data class SeatHoldInfo(
    @field:NotBlank(message = "좌석 번호는 필수입니다.")
    val seatNumber: String,

    @field:NotBlank(message = "좌석 선점 토큰은 필수입니다.")
    val occupyToken: String
)
