package com.back.domain.auth.controller;

import com.back.domain.user.entity.LoginType;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.security.jwt.RefreshTokenValidationResult;
import com.back.global.security.jwt.repository.BlacklistRepository;
import com.back.global.security.jwt.repository.RefreshTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@org.springframework.context.annotation.Import(com.back.global.RedisTestConfig.class)
class AuthControllerTest {
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private BlacklistRepository blacklistRepository;

    private static final String LOGIN_ID = "testuser";
    private static final String PASSWORD = "q1w2e3r4";

    @Autowired
    AuthControllerTest(
            MockMvc mockMvc,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = new ObjectMapper();
    }

    @BeforeEach
    void setUp() {
        userRepository.save(User.create(
                LOGIN_ID,
                "test@naver.com",
                passwordEncoder.encode(PASSWORD),
                "홍길동",
                LoginType.NORMAL
        ));
    }

    @Test
    @DisplayName("로그인 성공")
    void t1() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", LOGIN_ID,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andExpect(header().exists("Authorization"))
                .andExpect(header().string("Authorization", startsWith("Bearer ")))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("로그인 성공 및 인증 토큰이 발급되었습니다."));
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치")
    void t2() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", LOGIN_ID,
                                "password", "wrongpassword"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resultCode").value("401-2"))
                .andExpect(jsonPath("$.msg").value("비밀번호가 일치하지 않습니다."));
    }

    @Test
    @DisplayName("토큰 재발급 성공")
    void t3() throws Exception {
        Cookie refreshTokenCookie = loginAndGetRefreshTokenCookie();

        when(refreshTokenRepository.rotate(
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(Duration.class)
        )).thenReturn(RefreshTokenValidationResult.SUCCESS);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshTokenCookie))
                .andExpect(status().isOk())
                .andExpect(header().exists("Authorization"))
                .andExpect(header().string("Authorization", startsWith("Bearer ")))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("Access Token이 정상적으로 재발급되었습니다."));
    }

    @Test
    @DisplayName("토큰 재발급 실패 - refreshToken 쿠키 없음")
    void t4() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resultCode").value("401-8"))
                .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."));
    }

    @Test
    @DisplayName("로그아웃 성공")
    void t5() throws Exception {
        Cookie refreshTokenCookie = loginAndGetRefreshTokenCookie();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(refreshTokenCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refreshToken", 0))
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("로그아웃이 완료되었습니다. 토큰 및 세션 정보가 무효화되었습니다."));
    }

    @Test
    @DisplayName("로그아웃 성공 - accessToken 블랙리스트 등록")
    void t6() throws Exception {
        MvcResult loginResult = login();

        Cookie refreshTokenCookie = loginResult.getResponse().getCookie("refreshToken");
        String authorization = loginResult.getResponse().getHeader("Authorization");
        String accessToken = authorization.substring("Bearer ".length());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", authorization)
                        .cookie(refreshTokenCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refreshToken", 0))
                .andExpect(jsonPath("$.resultCode").value("200-1"));

        verify(blacklistRepository).add(eq(accessToken), any(Duration.class));
    }

    private Cookie loginAndGetRefreshTokenCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", LOGIN_ID,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshTokenCookie = result.getResponse().getCookie("refreshToken");

        assertThat(refreshTokenCookie).isNotNull();

        return refreshTokenCookie;
    }

    private MvcResult login() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", LOGIN_ID,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn();
    }
}
