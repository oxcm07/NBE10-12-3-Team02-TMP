package com.back.domain.user.controller

import com.back.domain.user.entity.LoginType
import com.back.domain.user.entity.User
import com.back.domain.user.repository.UserRepository
import com.back.global.RedisTestConfig
import com.back.global.security.SecurityUser
import com.back.global.security.jwt.JwtTokenProvider
import com.back.global.security.jwt.payload.AccessTokenPayload
import com.back.global.security.jwt.repository.BlacklistRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(RedisTestConfig::class)
class UserControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {
    private val objectMapper = ObjectMapper()
    private lateinit var userEntity: User
    private lateinit var securityUser: SecurityUser

    @MockitoBean
    private lateinit var blacklistRepository: BlacklistRepository

    @MockitoBean
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @BeforeEach
    fun setUp() {
        userEntity = userRepository.save(
            User.create(
                "testuser",
                "test@naver.com",
                passwordEncoder.encode("q1w2e3r4"),
                "홍길동",
                LoginType.NORMAL
            )
        )

        val userId = userEntity.userId ?: throw IllegalStateException("User ID null")
        securityUser = SecurityUser(userId, userEntity.name)

        `when`(jwtTokenProvider.parseAccessToken(anyString()))
            .thenReturn(AccessTokenPayload(userId, userEntity.name))

        `when`(jwtTokenProvider.getRemainingSeconds(anyString()))
            .thenReturn(600L)
    }

    @Test
    @DisplayName("회원가입 성공")
    fun t1() {
        mockMvc.perform(
            post("/api/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "id" to "testuser1",
                            "email" to "test1@naver.com",
                            "password" to "q1w2e3r4",
                            "name" to "홍길동"
                        )
                    )
                )
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("회원가입이 완료되었습니다."))
            .andExpect(jsonPath("$.data.userId").exists())
            .andExpect(jsonPath("$.data.loginType").value("NORMAL"))
    }

    @Test
    @DisplayName("회원가입 실패 - 아이디 중복")
    fun t2() {
        mockMvc.perform(
            post("/api/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "id" to "testuser",
                            "email" to "other@naver.com",
                            "password" to "q1w2e3r4",
                            "name" to "김철수"
                        )
                    )
                )
        )
            .andDo(print())
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.resultCode").value("409-1"))
            .andExpect(jsonPath("$.msg").value("이미 사용 중인 아이디입니다."))
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    fun t3() {
        mockMvc.perform(
            post("/api/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "id" to "otheruser",
                            "email" to "test@naver.com",
                            "password" to "q1w2e3r4",
                            "name" to "김철수"
                        )
                    )
                )
        )
            .andDo(print())
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.resultCode").value("409-2"))
            .andExpect(jsonPath("$.msg").value("이미 사용 중인 이메일입니다."))
    }

    @Test
    @DisplayName("회원가입 실패 - 비밀번호 8자 미만")
    fun t4() {
        mockMvc.perform(
            post("/api/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "id" to "testuser",
                            "email" to "test@naver.com",
                            "password" to "q1w2e3",
                            "name" to "홍길동"
                        )
                    )
                )
        )
            .andDo(print())
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("회원 탈퇴 성공")
    fun t5() {
        mockMvc.perform(
            patch("/api/v1/users/withdraw")
                .with(user(securityUser))
                .header("Authorization", "Bearer test-access-token")
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("회원 탈퇴가 정상적으로 완료되었습니다."))
    }

    @Test
    @DisplayName("회원 탈퇴 실패 - 존재하지 않는 회원")
    fun t6() {
        `when`(jwtTokenProvider.parseAccessToken(anyString()))
            .thenReturn(AccessTokenPayload(999L, "없는사용자"))

        mockMvc.perform(
            patch("/api/v1/users/withdraw")
                .with(user(SecurityUser(999L, "없는사용자")))
                .header("Authorization", "Bearer test-access-token")
        )
            .andDo(print())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resultCode").value("404-2"))
    }

    @Test
    @DisplayName("회원 탈퇴 실패 - 이미 탈퇴한 회원")
    fun t7() {
        userEntity.withdraw()
        userRepository.saveAndFlush(userEntity)

        mockMvc.perform(
            patch("/api/v1/users/withdraw")
                .with(user(securityUser))
                .header("Authorization", "Bearer test-access-token")
        )
            .andDo(print())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resultCode").value("404-2"))
    }

    @Test
    @DisplayName("마이페이지 조회 성공")
    fun t8() {
        mockMvc.perform(
            get("/api/v1/users/me")
                .with(user(securityUser))
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("마이페이지 조회 성공"))
            .andExpect(jsonPath("$.data.name").value("홍길동"))
            .andExpect(jsonPath("$.data.ticketGroups").isArray)
    }

    @Test
    @DisplayName("마이페이지 조회 실패 - 존재하지 않는 회원")
    fun t9() {
        mockMvc.perform(
            get("/api/v1/users/me")
                .with(user(SecurityUser(999L, "없는사용자")))
        )
            .andDo(print())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resultCode").value("404-1"))
    }

    @Test
    @DisplayName("마이페이지 수정 성공 - 이름만 변경")
    fun t10() {
        mockMvc.perform(
            patch("/api/v1/users/me")
                .with(user(securityUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("name" to "김철수")))
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("마이페이지 수정 성공"))

        val updated = userRepository.findById(userEntity.userId!!).orElseThrow()
        assertThat(updated.name).isEqualTo("김철수")
        assertThat(updated.email).isEqualTo("test@naver.com")
    }

    @Test
    @DisplayName("마이페이지 수정 실패 - 이메일 중복")
    fun t11() {
        userRepository.save(
            User.create("otheruser", "other@naver.com", passwordEncoder.encode("q1w2e3r4"), "김철수", LoginType.NORMAL)
        )

        mockMvc.perform(
            patch("/api/v1/users/me")
                .with(user(securityUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("email" to "other@naver.com")))
        )
            .andDo(print())
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.resultCode").value("409-2"))
    }

    @Test
    @DisplayName("마이페이지 수정 실패 - 존재하지 않는 회원")
    fun t12() {
        mockMvc.perform(
            patch("/api/v1/users/me")
                .with(user(SecurityUser(999L, "없는사용자")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("name" to "김철수")))
        )
            .andDo(print())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resultCode").value("404-1"))
    }

    @Test
    @DisplayName("아이디 중복확인 성공")
    fun t13() {
        mockMvc.perform(
            get("/api/v1/users/check-id")
                .param("id", "newuser123")
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("사용 가능한 아이디입니다."))
    }

    @Test
    @DisplayName("아이디 중복확인 실패 - 중복")
    fun t14() {
        userRepository.save(
            User.create("existuser", "exist@naver.com", passwordEncoder.encode("q1w2e3r4"), "홍길동", LoginType.NORMAL)
        )

        mockMvc.perform(
            get("/api/v1/users/check-id")
                .param("id", "existuser")
        )
            .andDo(print())
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.resultCode").value("409-1"))
            .andExpect(jsonPath("$.msg").value("이미 사용 중인 아이디입니다."))
    }
}
