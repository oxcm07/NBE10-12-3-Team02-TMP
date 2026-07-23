package com.back.global.security.oauth2.repository

import com.back.global.requestcontext.RequestContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.stereotype.Component
import org.springframework.util.SerializationUtils
import java.util.Base64

@Component
class HttpCookieOAuth2AuthorizationRequestRepository(
    private val requestContext: RequestContext
) : AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    override fun loadAuthorizationRequest(request: HttpServletRequest): OAuth2AuthorizationRequest? {
        val value = requestContext.getCookieValue(OAUTH2_COOKIE_NAME, "")
        if (value.isBlank()) return null

        return try {
            deserialize(value)
        } catch (e: RuntimeException) {
            requestContext.deleteCookie(OAUTH2_COOKIE_NAME, OAUTH2_COOKIE_PATH)
            null
        }
    }

    override fun saveAuthorizationRequest(
        authorizationRequest: OAuth2AuthorizationRequest?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        if (authorizationRequest == null) {
            requestContext.deleteCookie(OAUTH2_COOKIE_NAME, OAUTH2_COOKIE_PATH)
            return
        }

        requestContext.setCookieWithMaxAge(
            OAUTH2_COOKIE_NAME,
            serialize(authorizationRequest),
            OAUTH2_COOKIE_PATH,
            180
        )
    }

    override fun removeAuthorizationRequest(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): OAuth2AuthorizationRequest? {
        val authorizationRequest = loadAuthorizationRequest(request)
        requestContext.deleteCookie(OAUTH2_COOKIE_NAME, OAUTH2_COOKIE_PATH)
        return authorizationRequest
    }

    private fun serialize(authorizationRequest: OAuth2AuthorizationRequest): String {
        val bytes = SerializationUtils.serialize(authorizationRequest)
        return Base64.getUrlEncoder().encodeToString(bytes)
    }

    private fun deserialize(value: String): OAuth2AuthorizationRequest {
        val bytes = Base64.getUrlDecoder().decode(value)
        return SerializationUtils.deserialize(bytes) as OAuth2AuthorizationRequest
    }

    companion object {
        private const val OAUTH2_COOKIE_NAME = "oauth2_auth_request"
        private const val OAUTH2_COOKIE_PATH = "/"
    }
}
