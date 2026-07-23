package com.back.domain.auth.controller

import com.back.domain.auth.dto.TokenResponse
import com.back.domain.auth.service.AuthService
import com.back.domain.user.entity.LoginType
import com.back.domain.user.entity.User
import com.back.domain.user.repository.UserRepository
import com.back.global.RedisTestConfig
import com.back.global.security.oauth2.loginhandler.OAuth2LoginFailureHandler
import com.back.global.security.oauth2.loginhandler.OAuth2LoginSuccessHandler
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(RedisTestConfig::class)
class OAuth2LoginIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var successHandler: OAuth2LoginSuccessHandler

    @Autowired
    private lateinit var failureHandler: OAuth2LoginFailureHandler

    @MockitoBean
    private lateinit var userRepository: UserRepository

    @MockitoBean
    private lateinit var authService: AuthService

    @Test
    @DisplayName("카카오 OAuth2 로그인 진입 시 카카오 인증 서버로 redirect")
    fun t1() {
        mockMvc.perform(get("/oauth2/authorization/kakao"))
            .andExpect(status().is3xxRedirection)
            .andExpect(header().exists("Location"))
            .andExpect(header().string("Location", containsString("kauth.kakao.com")))
    }

    @Test
    @DisplayName("구글 OAuth2 로그인 진입 시 구글 인증 서버로 redirect")
    fun t2() {
        mockMvc.perform(get("/oauth2/authorization/google"))
            .andExpect(status().is3xxRedirection)
            .andExpect(header().exists("Location"))
            .andExpect(header().string("Location", containsString("accounts.google.com")))
    }

    @Test
    @DisplayName("OAuth2 로그인 성공 핸들러 - 토큰 발급 후 프론트로 redirect")
    fun t3() {
        val user = User.create(
            "GOOGLE_google-sub",
            "google@test.com",
            "encoded-password",
            "구글유저",
            LoginType.GOOGLE
        )

        `when`(userRepository.findByUserIdAndDeletedAtIsNull(1L))
            .thenReturn(Optional.of(user))

        `when`(authService.issueTokens(user))
            .thenReturn(TokenResponse("access-token", "refresh-token"))

        val response = MockHttpServletResponse()

        successHandler.onAuthenticationSuccess(
            mock(HttpServletRequest::class.java),
            response,
            authentication(oAuth2User(mapOf("userId" to 1L)))
        )

        assertThat(response.status).isEqualTo(302)
        assertThat(response.redirectedUrl)
            .isEqualTo("http://localhost:3000#accessToken=access-token")

        verify(authService).issueTokens(user)
    }

    @Test
    @DisplayName("OAuth2 로그인 실패 핸들러 - 에러 코드와 함께 프론트 로그인 페이지로 redirect")
    fun t4() {
        val response = MockHttpServletResponse()

        failureHandler.onAuthenticationFailure(
            mock(HttpServletRequest::class.java),
            response,
            OAuth2AuthenticationException("oauth2_email_already_exists")
        )

        assertThat(response.status).isEqualTo(302)
        assertThat(response.redirectedUrl)
            .isEqualTo("http://localhost:3000/login?error=oauth2_email_already_exists")
    }

    private fun authentication(oAuth2User: OAuth2User): Authentication {
        val authentication = mock(Authentication::class.java)
        `when`(authentication.principal).thenReturn(oAuth2User)
        return authentication
    }

    private fun oAuth2User(attributes: Map<String, Any>): OAuth2User {
        return DefaultOAuth2User(
            listOf(),
            attributes,
            if (attributes.containsKey("userId")) "userId" else "email"
        )
    }
}
