package com.back.global.security.filter

import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import com.back.global.requestcontext.RequestContext
import com.back.global.security.auth.SecurityAuthenticationFactory
import com.back.global.security.jwt.JwtTokenProvider
import com.back.global.security.jwt.repository.BlacklistRepository
import com.back.global.util.Ut
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class CustomAuthenticationFilter(
    private val requestContext: RequestContext,
    private val jwtTokenProvider: JwtTokenProvider,
    private val bearerTokenExtractor: BearerTokenExtractor,
    private val skipMatcher: CustomAuthenticationFilterSkipMatcher,
    private val securityAuthenticationFactory: SecurityAuthenticationFactory,
    private val blacklistRepository: BlacklistRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            authenticateByAccessToken()
            filterChain.doFilter(request, response)
        } catch (e: ServiceException) {
            SecurityContextHolder.clearContext()

            val rsData = e.rsData
            response.contentType = "application/json;charset=UTF-8"
            response.status = rsData.statusCode
            response.writer.write(
                Ut.json.toString(
                    rsData,
                    "{\"resultCode\":\"500-1\",\"msg\":\"JSON 변환 실패\",\"data\":null}"
                )
            )
        }
    }

    private fun authenticateByAccessToken() {
        val authorization = requestContext.getHeader("Authorization", "")

        if (authorization.isBlank()) {
            return
        }

        val accessToken = bearerTokenExtractor.extract(authorization)

        if (blacklistRepository.isBlacklisted(accessToken)) {
            throw ServiceException(ErrorCode.AUTH_LOGIN_REQUIRED)
        }

        val payload = jwtTokenProvider.parseAccessToken(accessToken)
        val authentication = securityAuthenticationFactory.create(payload)
        SecurityContextHolder.getContext().authentication = authentication
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return skipMatcher.shouldSkip(request)
    }
}
