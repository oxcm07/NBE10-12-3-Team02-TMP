package com.back.domain.auth.service

import com.back.domain.auth.dto.TokenResponse
import com.back.domain.user.entity.User
import com.back.domain.user.repository.UserRepository
import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import com.back.global.security.filter.BearerTokenExtractor
import com.back.global.security.jwt.JwtTokenProvider
import com.back.global.security.jwt.RefreshTokenValidationResult
import com.back.global.security.jwt.TokenHashUtil
import com.back.global.security.jwt.repository.BlacklistRepository
import com.back.global.security.jwt.repository.RefreshTokenRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

@Service
class AuthService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val blacklistRepository: BlacklistRepository,
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val passwordEncoder: PasswordEncoder,
    private val bearerTokenExtractor: BearerTokenExtractor,
    @Value("\${custom.jwt.refreshToken.expirationSeconds}") private val refreshTokenExpireSeconds: Int
) {

    fun login(id: String, password: String): TokenResponse {
        val user = userRepository.findByLoginIdAndDeletedAtIsNull(id)
            ?: throw ServiceException(ErrorCode.USER_NOT_FOUND)

        if (!passwordEncoder.matches(password, user.password)) {
            throw ServiceException(ErrorCode.AUTH_PASSWORD_MISMATCH)
        }

        return issueTokens(user)
    }

    fun logout(refreshToken: String?, authorization: String?) {
        deleteRefreshTokenIfValid(refreshToken)
        blacklistAccessTokenIfValid(authorization)
    }

    fun refresh(refreshToken: String): TokenResponse {
        val payload = jwtTokenProvider.parseRefreshToken(refreshToken)
            ?: throw ServiceException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN)

        val user = userRepository.findByUserIdAndDeletedAtIsNull(payload.userId)
            ?: throw ServiceException(ErrorCode.USER_NOT_FOUND)

        val requestRefreshTokenHash = TokenHashUtil.sha256(refreshToken)
        val newAccessToken = jwtTokenProvider.createAccessToken(user)

        val newJti = UUID.randomUUID().toString()
        val newRefreshToken = jwtTokenProvider.createRefreshToken(user, newJti)
        val newRefreshTokenHash = TokenHashUtil.sha256(newRefreshToken)

        val rotateResult = refreshTokenRepository.rotate(
            payload.userId,
            payload.jti,
            requestRefreshTokenHash,
            newJti,
            newRefreshTokenHash,
            refreshTokenTtl()
        )

        handleValidationFailure(rotateResult, payload.userId)

        return TokenResponse(newAccessToken, newRefreshToken)
    }

    fun restore(refreshToken: String): String {
        val payload = jwtTokenProvider.parseRefreshToken(refreshToken)
            ?: throw ServiceException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN)

        val requestRefreshTokenHash = TokenHashUtil.sha256(refreshToken)

        val result = refreshTokenRepository.verify(
            payload.userId,
            payload.jti,
            requestRefreshTokenHash
        )

        handleValidationFailure(result, payload.userId)

        val user = userRepository.findByUserIdAndDeletedAtIsNull(payload.userId)
            ?: throw ServiceException(ErrorCode.USER_NOT_FOUND)

        return jwtTokenProvider.createAccessToken(user)
    }

    private fun handleValidationFailure(validationResult: RefreshTokenValidationResult, userId: Long) {
        when (validationResult) {
            RefreshTokenValidationResult.SUCCESS -> return
            RefreshTokenValidationResult.MISMATCH -> {
                refreshTokenRepository.deleteAllByUserId(userId)
                throw ServiceException(ErrorCode.AUTH_REFRESH_TOKEN_MISMATCH)
            }
            RefreshTokenValidationResult.NOT_FOUND ->
                throw ServiceException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN)
        }
    }

    private fun deleteRefreshTokenIfValid(refreshToken: String?) {
        if (refreshToken.isNullOrBlank()) return
        val payload = jwtTokenProvider.parseRefreshToken(refreshToken)
        if (payload != null) {
            refreshTokenRepository.delete(payload.userId, payload.jti)
        }
    }

    private fun blacklistAccessTokenIfValid(authorization: String?) {
        val accessToken = bearerTokenExtractor.extractAccessTokenOrNull(authorization) ?: return

        try {
            val remaining = jwtTokenProvider.getRemainingSeconds(accessToken)
            if (remaining > 0) {
                blacklistRepository.add(
                    accessToken,
                    Duration.ofSeconds(remaining + BLACKLIST_GRACE_SECONDS)
                )
            }
        } catch (ignored: RuntimeException) {
        }
    }

    private fun refreshTokenTtl(): Duration =
        Duration.ofSeconds(refreshTokenExpireSeconds.toLong())

    fun issueTokens(user: User): TokenResponse {
        val userId = user.userId ?: throw IllegalArgumentException("User ID must not be null")
        val accessToken = jwtTokenProvider.createAccessToken(user)

        val refreshTokenJti = UUID.randomUUID().toString()
        val refreshToken = jwtTokenProvider.createRefreshToken(user, refreshTokenJti)
        val refreshTokenHash = TokenHashUtil.sha256(refreshToken)

        refreshTokenRepository.save(
            userId,
            refreshTokenJti,
            refreshTokenHash,
            refreshTokenTtl()
        )

        return TokenResponse(accessToken, refreshToken)
    }

    companion object {
        private const val BLACKLIST_GRACE_SECONDS = 60L
    }
}
