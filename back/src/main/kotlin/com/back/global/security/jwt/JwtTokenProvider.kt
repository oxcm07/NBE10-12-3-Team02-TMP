package com.back.global.security.jwt

import com.back.domain.user.entity.User
import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import com.back.global.security.jwt.payload.AccessTokenPayload
import com.back.global.security.jwt.payload.RefreshTokenPayload
import com.back.global.util.Ut
import io.jsonwebtoken.ExpiredJwtException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class JwtTokenProvider(
    @Value("\${custom.jwt.accessToken.secret}") private val accessTokenSecret: String,
    @Value("\${custom.jwt.refreshToken.secret}") private val refreshTokenSecret: String,
    @Value("\${custom.jwt.accessToken.expirationSeconds}") private val accessTokenExpireSeconds: Int,
    @Value("\${custom.jwt.refreshToken.expirationSeconds}") private val refreshTokenExpireSeconds: Int
) {

    fun parseRefreshToken(refreshToken: String): RefreshTokenPayload? {
        return try {
            val payload = Ut.jwt.payload(refreshTokenSecret, refreshToken)
            val userId = getLongClaim(payload, "id")
            val jti = getStringClaim(payload, "jti")
            RefreshTokenPayload(userId, jti)
        } catch (e: RuntimeException) {
            null
        }
    }

    fun parseAccessToken(accessToken: String): AccessTokenPayload {
        try {
            val payload = Ut.jwt.payload(accessTokenSecret, accessToken)
            val userId = getLongClaim(payload, "id")
            val name = getStringClaim(payload, "name")
            return AccessTokenPayload(userId, name)
        } catch (e: ExpiredJwtException) {
            throw ServiceException(ErrorCode.AUTH_EXPIRED_ACCESS_TOKEN)
        } catch (e: RuntimeException) {
            throw ServiceException(ErrorCode.AUTH_INVALID_ACCESS_TOKEN)
        }
    }

    fun getRemainingSeconds(accessToken: String): Long {
        val payload = Ut.jwt.payload(accessTokenSecret, accessToken) ?: return 0L
        val exp = payload["exp"] ?: return 0L
        val expTime = (exp as Number).toLong()
        val now = System.currentTimeMillis() / 1000
        return maxOf(0L, expTime - now)
    }

    private fun getLongClaim(payload: Map<String, Any>, key: String): Long {
        val value = payload[key] ?: throw IllegalArgumentException("Missing claim: $key")
        return when (value) {
            is Number -> value.toLong()
            else -> value.toString().toLong()
        }
    }

    private fun getStringClaim(payload: Map<String, Any>, key: String): String {
        val value = payload[key]
        if (value == null || value.toString().isBlank()) {
            throw IllegalArgumentException("Missing claim: $key")
        }
        return value.toString()
    }

    fun createAccessToken(user: User): String {
        val userId = user.userId ?: throw IllegalArgumentException("User ID must not be null")
        return Ut.jwt.toString(
            accessTokenSecret,
            accessTokenExpireSeconds,
            mapOf("id" to userId, "name" to user.name)
        )
    }

    fun createRefreshToken(user: User, jti: String): String {
        val userId = user.userId ?: throw IllegalArgumentException("User ID must not be null")
        return Ut.jwt.toString(
            refreshTokenSecret,
            refreshTokenExpireSeconds,
            mapOf("id" to userId, "name" to user.name, "jti" to jti)
        )
    }
}
