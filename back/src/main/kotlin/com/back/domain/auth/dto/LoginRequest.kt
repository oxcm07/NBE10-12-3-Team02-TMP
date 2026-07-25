package com.back.domain.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:NotBlank(message = "아이디를 입력해주세요.")
    val id: String,

    @field:NotBlank(message = "비밀번호를 입력해주세요.")
    @field:Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    val password: String
)
