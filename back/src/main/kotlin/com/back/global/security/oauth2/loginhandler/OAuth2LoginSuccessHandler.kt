package com.back.global.security.oauth2.loginhandler

import com.back.domain.auth.service.AuthService
import com.back.domain.user.repository.UserRepository
import com.back.global.requestcontext.RequestContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2LoginSuccessHandler(
    private val userRepository: UserRepository,
    private val authService: AuthService,
    private val requestContext: RequestContext,
    private val redirectHandler: OAuth2RedirectHandler
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oAuth2User = authentication.principal as OAuth2User
        val userIdAttribute = oAuth2User.getAttribute<Any>("userId")

        if (userIdAttribute == null) {
            redirectHandler.redirectFailure(response, "oauth2_user_id_missing")
            return
        }

        val userId = try {
            userIdAttribute.toString().toLong()
        } catch (e: NumberFormatException) {
            redirectHandler.redirectFailure(response, "oauth2_user_id_invalid")
            return
        }

        val user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
        if (user == null) {
            redirectHandler.redirectFailure(response, "oauth2_user_not_found")
            return
        }

        try {
            val tokenResponse = authService.issueTokens(user)
            requestContext.setCookie("refreshToken", tokenResponse.refreshToken, "/api/v1/auth")
            redirectHandler.redirectSuccess(response, tokenResponse.accessToken)
        } catch (e: RuntimeException) {
            redirectHandler.redirectFailure(response, "oauth2_token_issue_failed")
        }
    }
}
