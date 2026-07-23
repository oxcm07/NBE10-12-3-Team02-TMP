package com.back.global.security.oauth2.loginhandler

import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class OAuth2RedirectHandler(
    @Value("\${spring.security.oauth2.front-callback-url}") private val frontCallbackUrl: String,
    @Value("\${spring.security.oauth2.front-login-url}") private val frontLoginUrl: String
) {

    fun redirectSuccess(response: HttpServletResponse, accessToken: String) {
        val redirectUrl = UriComponentsBuilder
            .fromUriString(frontCallbackUrl)
            .fragment("accessToken=$accessToken")
            .build()
            .toUriString()

        response.sendRedirect(redirectUrl)
    }

    fun redirectFailure(response: HttpServletResponse, errorCode: String) {
        val redirectUrl = UriComponentsBuilder
            .fromUriString(frontLoginUrl)
            .queryParam("error", errorCode)
            .build()
            .toUriString()

        response.sendRedirect(redirectUrl)
    }
}
