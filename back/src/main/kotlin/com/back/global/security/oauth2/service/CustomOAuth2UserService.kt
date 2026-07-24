package com.back.global.security.oauth2.service

import com.back.domain.user.entity.LoginType
import com.back.domain.user.entity.User
import com.back.domain.user.repository.UserRepository
import com.back.global.security.oauth2.info.GoogleOAuth2UserInfo
import com.back.global.security.oauth2.info.KakaoOAuth2UserInfo
import com.back.global.security.oauth2.info.OAuth2UserInfo
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class CustomOAuth2UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) : DefaultOAuth2UserService() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = super.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId
        val refreshToken = userRequest.additionalParameters.getOrDefault("refresh_token", "").toString()

        log.info("OAuth2 additionalParameters: {}", userRequest.additionalParameters)
        log.info("OAuth2 refreshToken: {}", refreshToken)

        @Suppress("UNCHECKED_CAST")
        val attributes = oAuth2User.attributes as Map<String, Any>

        val user = when (registrationId) {
            "kakao" -> getOrCreateUser(attributes, LoginType.KAKAO, refreshToken)
            "google" -> getOrCreateUser(attributes, LoginType.GOOGLE, refreshToken)
            else -> throw OAuth2AuthenticationException("oauth2_provider_not_supported")
        }

        val userId = user.userId ?: throw IllegalStateException("User ID must not be null")

        return DefaultOAuth2User(
            oAuth2User.authorities,
            mapOf(
                "userId" to userId,
                "loginId" to user.loginId,
                "email" to user.email,
                "name" to user.name
            ),
            "userId"
        )
    }

    private fun createUserInfo(attributes: Map<String, Any>, loginType: LoginType): OAuth2UserInfo {
        return when (loginType) {
            LoginType.KAKAO -> KakaoOAuth2UserInfo(attributes)
            LoginType.GOOGLE -> GoogleOAuth2UserInfo(attributes)
            else -> throw OAuth2AuthenticationException("oauth2_provider_not_supported")
        }
    }

    private fun getOrCreateUser(attributes: Map<String, Any>, loginType: LoginType, refreshToken: String): User {
        val userInfo = createUserInfo(attributes, loginType)

        val platformId = userInfo.providerId
        val loginId = "${loginType.name}_$platformId"
        val email = userInfo.email
        val name = userInfo.name ?: ""

        validateRequired(platformId, "oauth2_provider_id_missing")
        validateRequired(email, "oauth2_email_missing")

        val safeEmail = email ?: throw OAuth2AuthenticationException("oauth2_email_missing")

        val existingUser = userRepository.findByLoginIdAndDeletedAtIsNull(loginId)
        if (existingUser != null) {
            if (refreshToken.isNotBlank()) {
                existingUser.updateOauthRefreshToken(refreshToken)
            }
            return existingUser
        }

        return createOAuthUser(loginId, safeEmail, name, loginType, refreshToken)
    }

    private fun createOAuthUser(
        loginId: String,
        email: String,
        name: String,
        loginType: LoginType,
        refreshToken: String
    ): User {
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw OAuth2AuthenticationException("oauth2_email_already_exists")
        }

        val encodedPassword = passwordEncoder.encode(UUID.randomUUID().toString())
        val randomPassword = requireNotNull(encodedPassword) { "Password encoding failed" }

        val user = User.createOAuth(
            loginId = loginId,
            email = email,
            password = randomPassword,
            name = name,
            loginType = loginType,
            oauthRefreshToken = if (refreshToken.isBlank()) null else refreshToken
        )

        return try {
            userRepository.save(user)
        } catch (e: DataIntegrityViolationException) {
            throw OAuth2AuthenticationException("oauth2_email_already_exists")
        }
    }

    private fun validateRequired(value: String?, errorCode: String) {
        if (value.isNullOrBlank()) {
            throw OAuth2AuthenticationException(errorCode)
        }
    }
}
