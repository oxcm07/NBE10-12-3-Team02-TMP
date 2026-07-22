package com.back.domain.auth.controller;

import com.back.domain.auth.dto.TokenResponse;
import com.back.domain.auth.service.AuthService;
import com.back.domain.user.entity.LoginType;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.security.oauth2.loginhandler.OAuth2LoginFailureHandler;
import com.back.global.security.oauth2.loginhandler.OAuth2LoginSuccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@org.springframework.context.annotation.Import(com.back.global.RedisTestConfig.class)
class OAuth2LoginIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    OAuth2LoginSuccessHandler successHandler;

    @Autowired
    OAuth2LoginFailureHandler failureHandler;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    AuthService authService;

    @Test
    @DisplayName("카카오 OAuth2 로그인 진입 시 카카오 인증 서버로 redirect")
    void t1() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", containsString("kauth.kakao.com")));
    }

    @Test
    @DisplayName("구글 OAuth2 로그인 진입 시 구글 인증 서버로 redirect")
    void t2() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", containsString("accounts.google.com")));
    }

    @Test
    @DisplayName("OAuth2 로그인 성공 핸들러 - 토큰 발급 후 프론트로 redirect")
    void t3() throws Exception {
        User user = User.create(
                "GOOGLE_google-sub",
                "google@test.com",
                "encoded-password",
                "구글유저",
                LoginType.GOOGLE
        );

        when(userRepository.findByUserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(user));

        when(authService.issueTokens(user))
                .thenReturn(new TokenResponse("access-token", "refresh-token"));

        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(
                mock(HttpServletRequest.class),
                response,
                authentication(oAuth2User(Map.of("userId", 1L)))
        );

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000#accessToken=access-token");

        verify(authService).issueTokens(user);
    }

    @Test
    @DisplayName("OAuth2 로그인 실패 핸들러 - 에러 코드와 함께 프론트 로그인 페이지로 redirect")
    void t4() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        failureHandler.onAuthenticationFailure(
                mock(HttpServletRequest.class),
                response,
                new OAuth2AuthenticationException("oauth2_email_already_exists")
        );

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/login?error=oauth2_email_already_exists");
    }

    private Authentication authentication(OAuth2User oAuth2User) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        return authentication;
    }

    private OAuth2User oAuth2User(Map<String, Object> attributes) {
        return new DefaultOAuth2User(
                List.of(),
                attributes,
                attributes.containsKey("userId") ? "userId" : "email"
        );
    }
}