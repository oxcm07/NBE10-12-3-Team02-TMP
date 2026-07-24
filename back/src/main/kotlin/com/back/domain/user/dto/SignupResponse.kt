package com.back.domain.user.dto

import com.back.domain.user.entity.User

data class SignupResponse(
    val userId: Long,
    val loginType: String
) {
    companion object {
        fun from(user: User): SignupResponse {
            val userId = user.userId ?: throw IllegalArgumentException("User ID must not be null")
            return SignupResponse(userId, user.loginType.name)
        }
    }
}
