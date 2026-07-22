package com.back.domain.user.controller;

import com.back.domain.user.entity.LoginType;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.RedisTestConfig;
import com.back.global.security.SecurityUser;
import com.back.global.security.jwt.repository.BlacklistRepository;
import com.back.global.security.jwt.JwtTokenProvider;
import com.back.global.security.jwt.payload.AccessTokenPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(RedisTestConfig.class)
class UserControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private User user;
    private SecurityUser securityUser;

    @MockitoBean
    private BlacklistRepository blacklistRepository;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    public UserControllerTest(
            MockMvc mockMvc,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.create(
                "testuser",
                "test@naver.com",
                passwordEncoder.encode("q1w2e3r4"),
                "홍길동",
                LoginType.NORMAL
        ));

        securityUser = new SecurityUser(
                user.getUserId(),
                user.getName()
        );

        Mockito.when(jwtTokenProvider.parseAccessToken(Mockito.anyString()))
                .thenReturn(new AccessTokenPayload(user.getUserId(), user.getName()));

        Mockito.when(jwtTokenProvider.getRemainingSeconds(Mockito.anyString()))
                .thenReturn(600L);
    }

    @Test
    @DisplayName("회원가입 성공")
    void t1() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", "testuser1",
                                "email", "test1@naver.com",
                                "password", "q1w2e3r4",
                                "name", "홍길동"
                        ))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("회원가입이 완료되었습니다."))
                .andExpect(jsonPath("$.data.userId").exists())
                .andExpect(jsonPath("$.data.loginType").value("NORMAL"));
    }

    @Test
    @DisplayName("회원가입 실패 - 아이디 중복")
    void t2() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", "testuser",
                                "email", "other@naver.com",
                                "password", "q1w2e3r4",
                                "name", "김철수"
                        ))))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-1"))
                .andExpect(jsonPath("$.msg").value("이미 사용 중인 아이디입니다."));
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void t3() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", "otheruser",
                                "email", "test@naver.com",
                                "password", "q1w2e3r4",
                                "name", "김철수"
                        ))))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-2"))
                .andExpect(jsonPath("$.msg").value("이미 사용 중인 이메일입니다."));
    }

    @Test
    @DisplayName("회원가입 실패 - 비밀번호 8자 미만")
    void t4() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", "testuser",
                                "email", "test@naver.com",
                                "password", "q1w2e3",
                                "name", "홍길동"
                        ))))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("회원 탈퇴 성공")
    void t5() throws Exception {
        mockMvc.perform(patch("/api/v1/users/withdraw")
                        .with(user(securityUser))
                        .header("Authorization", "Bearer test-access-token"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("회원 탈퇴가 정상적으로 완료되었습니다."));
    }

    @Test
    @DisplayName("회원 탈퇴 실패 - 존재하지 않는 회원")
    void t6() throws Exception {
        Mockito.when(jwtTokenProvider.parseAccessToken(Mockito.anyString()))
                .thenReturn(new AccessTokenPayload(999L, "없는사용자"));

        mockMvc.perform(patch("/api/v1/users/withdraw")
                        .with(user(new SecurityUser(999L, "없는사용자")))
                        .header("Authorization", "Bearer test-access-token"))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-2"));
    }

    @Test
    @DisplayName("회원 탈퇴 실패 - 이미 탈퇴한 회원")
    void t7() throws Exception {
        user.withdraw();
        userRepository.saveAndFlush(user);

        mockMvc.perform(patch("/api/v1/users/withdraw")
                        .with(user(securityUser))
                        .header("Authorization", "Bearer test-access-token"))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-2"));
    }

    @Test
    @DisplayName("마이페이지 조회 성공")
    void t8() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(user(securityUser)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("마이페이지 조회 성공"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.ticketGroups").isArray());
    }

    @Test
    @DisplayName("마이페이지 조회 실패 - 존재하지 않는 회원")
    void t9() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(user(new SecurityUser(999L, "없는사용자"))))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"));
    }

    @Test
    @DisplayName("마이페이지 수정 성공 - 이름만 변경")
    void t10() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .with(user(securityUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "김철수"
                        ))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("마이페이지 수정 성공"));

        User updated = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("김철수");
        assertThat(updated.getEmail()).isEqualTo("test@naver.com");
    }

    @Test
    @DisplayName("마이페이지 수정 실패 - 이메일 중복")
    void t11() throws Exception {
        userRepository.save(User.create("otheruser", "other@naver.com",
                passwordEncoder.encode("q1w2e3r4"), "김철수", LoginType.NORMAL));

        mockMvc.perform(patch("/api/v1/users/me")
                        .with(user(securityUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "other@naver.com"
                        ))))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-2"));
    }

    @Test
    @DisplayName("마이페이지 수정 실패 - 존재하지 않는 회원")
    void t12() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .with(user(new SecurityUser(999L, "없는사용자")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "김철수"
                        ))))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"));
    }

    @Test
    @DisplayName("아이디 중복확인 성공")
    void t13() throws Exception {
        mockMvc.perform(get("/api/v1/users/check-id")
                        .param("id", "newuser123"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("사용 가능한 아이디입니다."));
    }

    @Test
    @DisplayName("아이디 중복확인 실패 - 중복")
    void t14() throws Exception {
        userRepository.save(User.create("existuser", "exist@naver.com",
                passwordEncoder.encode("q1w2e3r4"), "홍길동", LoginType.NORMAL));

        mockMvc.perform(get("/api/v1/users/check-id")
                        .param("id", "existuser"))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-1"))
                .andExpect(jsonPath("$.msg").value("이미 사용 중인 아이디입니다."));
    }
}