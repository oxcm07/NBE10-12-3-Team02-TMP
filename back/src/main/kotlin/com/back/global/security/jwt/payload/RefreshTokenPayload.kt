package com.back.global.security.jwt.payload

data class RefreshTokenPayload(
    val userId: Long,
    val jti: String
)
