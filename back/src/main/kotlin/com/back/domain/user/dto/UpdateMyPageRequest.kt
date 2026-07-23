package com.back.domain.user.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdateMyPageRequest(
    @field:Pattern(regexp = "^\\S+$", message = "이름에 공백을 포함할 수 없습니다.")
    val name: String? = null,

    @field:Email
    val email: String? = null,

    @field:Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    val password: String? = null
)
