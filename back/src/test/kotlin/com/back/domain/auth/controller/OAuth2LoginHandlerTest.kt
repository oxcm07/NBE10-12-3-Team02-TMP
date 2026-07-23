package com.back.domain.auth.controller

import com.back.domain.auth.dto.TokenResponse
import com.back.domain.auth.service.AuthService
import com.back.domain.user.entity.LoginType
import com.back.domain.user.entity.User
import com.back.domain.user.repository.UserRepository
import com.back.global.requestcontext.RequestContext
import com.back.global.security.oauth2.info.GoogleOAuth2UserInfo
import com.back.global.security.oauth2.info.KakaoOAuth2UserInfo
import com.back.global.security.oauth2.loginhandler.OAuth2LoginFailureHandler
import com.back.global.security.oauth2.loginhandler.OAuth2LoginSuccessHandler
import com.back.global.security.oauth2.loginhandler.OAuth2RedirectHandler
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

class OAuth2LoginHandlerTest {

    private val redirectHandler = OAuth2RedirectHandler("http://localhost:3000", "http://localhost:3000/login")

    @BeforeEach
    fun setUp() {
        ReflectionTestUtils.setField(redirectHandler, "frontCallbackUrl", "http://localhost:3000")
        ReflectionTestUtils.setField(redirectHandler, "frontLoginUrl", "http://localhost:3000/login")
    }

    @Test
    @DisplayName("카카오 OAuth2 사용자 정보 파싱")
    fun t1() {
        val userInfo = KakaoOAuth2UserInfo(
            mapOf(
                "id" to 12345L,
                "kakao_account" to mapOf(
                    "email" to "kakao@test.com",
                    "profile" to mapOf("nickname" to "카카오유저")
                )
            )
        )

        assertThat(userInfo.providerId).isEqualTo("12345")
        assertThat(userInfo.email).isEqualTo("kakao@test.com")
        assertThat(userInfo.name).isEqualTo("카카오유저")
    }

    @Test
    @DisplayName("구글 OAuth2 사용자 정보 파싱")
    fun t2() {
        val userInfo = GoogleOAuth2UserInfo(
            mapOf(
                "sub" to "google-sub",
                "email" to "google@test.com",
                "name" to "구글유저"
            )
        )

        assertThat(userInfo.providerId).isEqualTo("google-sub")
        assertThat(userInfo.email).isEqualTo("google@test.com")
        assertThat(userInfo.name).isEqualTo("구글유저")
    }

    @Test
    @DisplayName("OAuth2 로그인 성공 시 토큰 발급, refreshToken 쿠키 설정, accessToken fragment redirect")
    fun t3() {
        val userRepository = mock(UserRepository::class.java)
        val authService = mock(AuthService::class.java)
        val requestContext = mock(RequestContext::class.java)
        val successHandler = OAuth2LoginSuccessHandler(
            userRepository,
            authService,
            requestContext,
            redirectHandler
        )

        val user = User.create(
            "GOOGLE_google-sub",
            "google@test.com",
            "encoded-password",
            "구글유저",
            LoginType.GOOGLE
        )

        `when`(userRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user))
        `when`(authService.issueTokens(user)).thenReturn(TokenResponse("access-token", "refresh-token"))

        val response = MockHttpServletResponse()

        successHandler.onAuthenticationSuccess(
            mock(HttpServletRequest::class.java),
            response,
            authentication(oAuth2User(mapOf("userId" to 1L)))
        )

        verify(requestContext).setCookie("refreshToken", "refresh-token", "/api/v1/auth")
        assertThat(response.status).isEqualTo(302)
        assertThat(response.redirectedUrl).isEqualTo("http://localhost:3000#accessToken=access-token")
    }

    @Test
    @DisplayName("OAuth2 로그인 성공 핸들러 - userId 누락 시 실패 redirect")
    fun t4() {
        val successHandler = successHandler(
            mock(UserRepository::class.java),
            mock(AuthService::class.java),
            mock(RequestContext::class.java)
        )
        val response = MockHttpServletResponse()

        successHandler.onAuthenticationSuccess(
            mock(HttpServletRequest::class.java),
            response,
            authentication(oAuth2User(mapOf("email" to "google@test.com")))
        )

        assertThat(response.status).isEqualTo(302)
        assertThat(response.redirectedUrl).isEqualTo("http://localhost:3000/login?error=oauth2_user_id_missing")
    }

    @Test
    @DisplayName("OAuth2 로그인 성공 핸들러 - userId 형식 오류 시 실패 redirect")
    fun t5() {
        val successHandler = successHandler(
            mock(UserRepository::class.java),
            mock(AuthService::class.java),
            mock(RequestContext::class.java)
        )
        val response = MockHttpServletResponse()

        successHandler.onAuthenticationSuccess(
            mock(HttpServletRequest::class.java),
            response,
            authentication(oAuth2User(mapOf("userId" to "invalid-user-id")))
        )

        assertThat(response.status).isEqualTo(302)
        assertThat(response.redirectedUrl).isEqualTo("http://localhost:3000/login?error=oauth2_user_id_invalid")
    }

    @Test
    @DisplayName("OAuth2 로그인 성공 핸들러 - 회원 조회 실패 시 실패 redirect")
    fun t6() {
        val userRepository = mock(UserRepository::class.java)
        `when`(userRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty())

        val successHandler = successHandler(
            userRepository,
            mock(AuthService::class.java),
            mock(RequestContext::class.java)
        )
        val response = MockHttpServletResponse()

        successHandler.onAuthenticationSuccess(
            mock(HttpServletRequest::class.java),
            response,
            authentication(oAuth2User(mapOf("userId" to 1L)))
        )

        assertThat(response.status).isEqualTo(302)
        assertThat(response.redirectedUrl).isEqualTo("http://localhost:3000/login?error=oauth2_user_not_found")
    }

    @Test
    @DisplayName("OAuth2 로그인 성공 핸들러 - 토큰 발급 실패 시 실패 redirect")
    fun t7() {
        val userRepository = mock(UserRepository::class.java)
        val authService = mock(AuthService::class.java)
        val requestContext = mock(RequestContext::class.java)
        val user = User.create("GOOGLE_google-sub", "google@test.com", "encoded-password", "구글유저", LoginType.GOOGLE)

        `when`(userRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user))
        `when`(authService.issueTokens(user)).thenThrow(RuntimeException("redis down"))

        val successHandler = successHandler(userRepository, authService, requestContext)
        val response = MockHttpServletResponse()

        successHandler.onAuthenticationSuccess(
            mock(HttpServletRequest::class.java),
            response,
            authentication(oAuth2User(mapOf("userId" to 1L)))
        )

        verify(requestContext, never()).setCookie(anyString(), anyString(), anyString())
        assertThat(response.status).isEqualTo(302)
        assertThat(response.redirectedUrl).isEqualTo("http://localhost:3000/login?error=oauth2_token_issue_failed")
    }

    @Test
    @DisplayName("OAuth2 로그인 실패 시 OAuth2 에러 코드로 redirect")
    fun t8() {
        val failureHandler = OAuth2LoginFailureHandler(redirectHandler)
        val response = MockHttpServletResponse()

        failureHandler.onAuthenticationFailure(
            mock(HttpServletRequest::class.java),
            response,
            OAuth2AuthenticationException("oauth2_email_already_exists")
        )

        assertThat(response.status).isEqualTo(302)
        assertThat(response.redirectedUrl).isEqualTo("http://localhost:3000/login?error=oauth2_email_already_exists")
    }

    private fun successHandler(
        userRepository: UserRepository,
        authService: AuthService,
        requestContext: RequestContext
    ): OAuth2LoginSuccessHandler {
        return OAuth2LoginSuccessHandler(userRepository, authService, requestContext, redirectHandler)
    }

    private fun authentication(oAuth2User: OAuth2User): Authentication {
        val authentication = mock(Authentication::class.java)
        `when`(authentication.principal).thenReturn(oAuth2User)
        return authentication
    }

    private fun oAuth2User(attributes: Map<String, Any>): OAuth2User {
        return DefaultOAuth2User(listOf(), attributes, if (attributes.containsKey("userId")) "userId" else "email")
    }
}
