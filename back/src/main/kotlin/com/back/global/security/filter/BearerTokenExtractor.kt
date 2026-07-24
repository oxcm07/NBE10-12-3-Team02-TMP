package com.back.global.security.filter

import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import org.springframework.stereotype.Component

@Component
class BearerTokenExtractor {

    fun extract(authorization: String): String =
        authorization.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw ServiceException(ErrorCode.AUTH_INVALID_BEARER_HEADER)

    fun extractAccessTokenOrNull(authorization: String?): String? =
        authorization?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
