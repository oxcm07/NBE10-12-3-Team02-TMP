package com.back.global.security.jwt.payload

data class AccessTokenPayload(
    val userId: Long,
    val name: String
)
