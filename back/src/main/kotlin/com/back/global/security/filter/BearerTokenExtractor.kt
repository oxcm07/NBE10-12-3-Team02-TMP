package com.back.global.security.filter

import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import org.springframework.stereotype.Component

@Component
class BearerTokenExtractor {

    fun extract(authorization: String): String {
        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw ServiceException(ErrorCode.AUTH_INVALID_BEARER_HEADER)
        }

        val accessToken = authorization.substring(BEARER_PREFIX.length).trim()

        if (accessToken.isBlank()) {
            throw ServiceException(ErrorCode.AUTH_INVALID_BEARER_HEADER)
        }

        return accessToken
    }

    fun extractAccessTokenOrNull(authorization: String?): String? {
        if (authorization.isNullOrBlank()) {
            return null
        }

        if (!authorization.startsWith("Bearer ")) {
            return null
        }

        val accessToken = authorization.substring("Bearer ".length).trim()

        if (accessToken.isBlank()) {
            return null
        }

        return accessToken
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
