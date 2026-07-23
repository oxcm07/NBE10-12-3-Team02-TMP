package com.back.domain.auth.controller

import com.back.domain.user.entity.LoginType
import com.back.domain.user.entity.User
import com.back.domain.user.repository.UserRepository
import com.back.global.RedisTestConfig
import com.back.global.security.jwt.RefreshTokenValidationResult
import com.back.global.security.jwt.repository.BlacklistRepository
import com.back.global.security.jwt.repository.RefreshTokenRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(RedisTestConfig::class)
class AuthControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {
    private val objectMapper = ObjectMapper()

    @MockitoBean
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @MockitoBean
    private lateinit var blacklistRepository: BlacklistRepository

    @BeforeEach
    fun setUp() {
        userRepository.save(
            User.create(
                loginId = LOGIN_ID,
                email = "test@naver.com",
                password = passwordEncoder.encode(PASSWORD),
                name = "홍길동",
                loginType = LoginType.NORMAL
            )
        )
    }

    @Test
    @DisplayName("로그인 성공")
    fun t1() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("id" to LOGIN_ID, "password" to PASSWORD)))
        )
            .andExpect(status().isOk)
            .andExpect(header().exists("Authorization"))
            .andExpect(header().string("Authorization", startsWith("Bearer ")))
            .andExpect(cookie().exists("refreshToken"))
            .andExpect(cookie().httpOnly("refreshToken", true))
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("로그인 성공 및 인증 토큰이 발급되었습니다."))
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치")
    fun t2() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("id" to LOGIN_ID, "password" to "wrongpassword")))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resultCode").value("401-2"))
            .andExpect(jsonPath("$.msg").value("비밀번호가 일치하지 않습니다."))
    }

    @Test
    @DisplayName("토큰 재발급 성공")
    fun t3() {
        val refreshTokenCookie = loginAndGetRefreshTokenCookie()

        `when`(
            refreshTokenRepository.rotate(
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(Duration::class.java)
            )
        ).thenReturn(RefreshTokenValidationResult.SUCCESS)

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .cookie(refreshTokenCookie)
        )
            .andExpect(status().isOk)
            .andExpect(header().exists("Authorization"))
            .andExpect(header().string("Authorization", startsWith("Bearer ")))
            .andExpect(cookie().exists("refreshToken"))
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("Access Token이 정상적으로 재발급되었습니다."))
    }

    @Test
    @DisplayName("토큰 재발급 실패 - refreshToken 쿠키 없음")
    fun t4() {
        mockMvc.perform(post("/api/v1/auth/refresh"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resultCode").value("401-8"))
            .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."))
    }

    @Test
    @DisplayName("로그아웃 성공")
    fun t5() {
        val refreshTokenCookie = loginAndGetRefreshTokenCookie()

        mockMvc.perform(
            post("/api/v1/auth/logout")
                .cookie(refreshTokenCookie)
        )
            .andExpect(status().isOk)
            .andExpect(cookie().maxAge("refreshToken", 0))
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("로그아웃이 완료되었습니다. 토큰 및 세션 정보가 무효화되었습니다."))
    }

    @Test
    @DisplayName("로그아웃 성공 - accessToken 블랙리스트 등록")
    fun t6() {
        val loginResult = login()

        val refreshTokenCookie = loginResult.response.getCookie("refreshToken")
        val authorization = loginResult.response.getHeader("Authorization")!!
        val accessToken = authorization.substring("Bearer ".length)

        mockMvc.perform(
            post("/api/v1/auth/logout")
                .header("Authorization", authorization)
                .cookie(refreshTokenCookie)
        )
            .andExpect(status().isOk)
            .andExpect(cookie().maxAge("refreshToken", 0))
            .andExpect(jsonPath("$.resultCode").value("200-1"))

        verify(blacklistRepository).add(eq(accessToken), any(Duration::class.java))
    }

    private fun loginAndGetRefreshTokenCookie(): Cookie {
        val result = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("id" to LOGIN_ID, "password" to PASSWORD)))
        )
            .andExpect(status().isOk)
            .andReturn()

        val refreshTokenCookie = result.response.getCookie("refreshToken")
        assertThat(refreshTokenCookie).isNotNull
        return refreshTokenCookie!!
    }

    private fun login(): MvcResult {
        return mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("id" to LOGIN_ID, "password" to PASSWORD)))
        )
            .andExpect(status().isOk)
            .andReturn()
    }

    companion object {
        private const val LOGIN_ID = "testuser"
        private const val PASSWORD = "q1w2e3r4"
    }
}
