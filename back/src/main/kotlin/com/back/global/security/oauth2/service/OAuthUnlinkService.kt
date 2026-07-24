package com.back.global.security.oauth2.service

import com.back.domain.user.entity.LoginType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate

@Service
class OAuthUnlinkService(
    private val restTemplate: RestTemplate,
    @Value("\${spring.security.oauth2.client.registration.kakao.client-id}") private val kakaoClientId: String,
    @Value("\${spring.security.oauth2.client.registration.kakao.client-secret}") private val kakaoClientSecret: String,
    @Value("\${spring.security.oauth2.client.registration.google.client-id}") private val googleClientId: String,
    @Value("\${spring.security.oauth2.client.registration.google.client-secret}") private val googleClientSecret: String
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun unlink(loginType: LoginType, oauthRefreshToken: String?) {
        if (oauthRefreshToken.isNullOrBlank()) {
            log.warn("OAuth Refresh Token 없음, 언링크 스킵: {}", loginType)
            return
        }

        try {
            val accessToken = reissueAccessToken(loginType, oauthRefreshToken)
            when (loginType) {
                LoginType.KAKAO -> unlinkKakao(accessToken)
                LoginType.GOOGLE -> unlinkGoogle(accessToken)
                else -> log.warn("지원하지 않는 OAuth 플랫폼: {}", loginType)
            }
        } catch (e: Exception) {
            log.warn("OAuth 언링크 실패 - loginType: {}, error: {}", loginType, e.message)
        }
    }

    private fun reissueAccessToken(loginType: LoginType, refreshToken: String): String {
        return when (loginType) {
            LoginType.KAKAO -> reissueKakaoAccessToken(refreshToken)
            LoginType.GOOGLE -> reissueGoogleAccessToken(refreshToken)
            else -> throw IllegalArgumentException("지원하지 않는 OAuth 플랫폼: $loginType")
        }
    }

    private fun reissueKakaoAccessToken(refreshToken: String): String {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
        }

        val params = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "refresh_token")
            add("client_id", kakaoClientId)
            add("client_secret", kakaoClientSecret)
            add("refresh_token", refreshToken)
        }

        @Suppress("UNCHECKED_CAST")
        val response = restTemplate.postForObject(
            "https://kauth.kakao.com/oauth/token",
            HttpEntity(params, headers),
            Map::class.java
        ) as? Map<String, Any> ?: throw IllegalStateException("Kakao token response is null")

        return response["access_token"] as String
    }

    private fun reissueGoogleAccessToken(refreshToken: String): String {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
        }

        val params = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "refresh_token")
            add("client_id", googleClientId)
            add("client_secret", googleClientSecret)
            add("refresh_token", refreshToken)
        }

        @Suppress("UNCHECKED_CAST")
        val response = restTemplate.postForObject(
            "https://oauth2.googleapis.com/token",
            HttpEntity(params, headers),
            Map::class.java
        ) as? Map<String, Any> ?: throw IllegalStateException("Google token response is null")

        return response["access_token"] as String
    }

    private fun unlinkKakao(accessToken: String) {
        val headers = HttpHeaders().apply {
            setBearerAuth(accessToken)
        }

        restTemplate.postForObject(
            "https://kapi.kakao.com/v1/user/unlink",
            HttpEntity<Any>(headers),
            String::class.java
        )
    }

    private fun unlinkGoogle(accessToken: String) {
        restTemplate.postForObject(
            "https://oauth2.googleapis.com/revoke?token=$accessToken",
            HttpEntity.EMPTY,
            String::class.java
        )
    }
}
