package com.back.global.security.oauth2.loginhandler

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component

@Component
class OAuth2LoginFailureHandler(
    private val redirectHandler: OAuth2RedirectHandler
) : AuthenticationFailureHandler {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException
    ) {
        val errorCode = (exception as? OAuth2AuthenticationException)
            ?.error
            ?.errorCode
            ?: "oauth2_login_failed"

        redirectHandler.redirectFailure(response, errorCode)
    }
}
