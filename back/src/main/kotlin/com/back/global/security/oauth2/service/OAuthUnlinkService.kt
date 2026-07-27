package com.back.global.security.oauth2.service

import com.back.domain.user.entity.LoginType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestTemplate
import kotlin.collections.get

@Service
class OAuthUnlinkService(
    private val restTemplate: RestTemplate,
    @Value("\${spring.security.oauth2.client.registration.kakao.client-id}") private val kakaoClientId: String,
    @Value("\${spring.security.oauth2.client.registration.kakao.client-secret}") private val kakaoClientSecret: String,
    @Value("\${spring.security.oauth2.client.registration.naver.client-id:}") private val naverClientId: String,
    @Value("\${spring.security.oauth2.client.registration.naver.client-secret:}") private val naverClientSecret: String,
) {

    fun unlink(loginType: LoginType, oauthRefreshToken: String?) {
        if (oauthRefreshToken.isNullOrBlank()) {
            log.warn("OAuth Refresh Token 없음, 언링크 스킵: {}", loginType)
            return
        }

        try {
            when (loginType) {
                LoginType.NAVER -> revokeNaverToken(oauthRefreshToken)
                LoginType.GOOGLE -> revokeGoogleToken(oauthRefreshToken)
                LoginType.KAKAO -> unlinkKakao(reissueKakaoAccessToken(oauthRefreshToken))
                else -> log.warn("지원하지 않는 OAuth 플랫폼: {}", loginType)
            }
        } catch (e: Exception) {
            log.warn("OAuth 언링크 실패 - loginType: {}, error: {}", loginType, e.message)
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

    private fun revokeNaverToken(refreshToken: String) {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
        }

        val params = LinkedMultiValueMap<String, String>().apply {
            add("client_id", naverClientId)
            add("client_secret", naverClientSecret)
            add("token", refreshToken)
            add("token_type_hint", "refresh_token")
        }

        restTemplate.postForObject(
            "https://nid.naver.com/oauth2.0/revoke",
            HttpEntity<MultiValueMap<String, String>>(params, headers),
            String::class.java,
        )
    }

    private fun revokeGoogleToken(refreshToken: String) {
        restTemplate.postForObject(
            "https://oauth2.googleapis.com/revoke?token=$refreshToken",
            HttpEntity.EMPTY,
            String::class.java,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(OAuthUnlinkService::class.java)
    }
}
