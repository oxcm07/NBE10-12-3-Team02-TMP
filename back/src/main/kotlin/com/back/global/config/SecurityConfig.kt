package com.back.global.config

import com.back.global.exception.ErrorCode
import com.back.global.rsData.RsData
import com.back.global.security.filter.CustomAuthenticationFilter
import com.back.global.security.oauth2.converter.CustomTokenResponseConverter
import com.back.global.security.oauth2.loginhandler.OAuth2LoginFailureHandler
import com.back.global.security.oauth2.loginhandler.OAuth2LoginSuccessHandler
import com.back.global.security.oauth2.repository.HttpCookieOAuth2AuthorizationRequestRepository
import com.back.global.security.oauth2.service.CustomOAuth2UserService
import com.back.global.util.Ut
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.converter.FormHttpMessageConverter
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.client.RestClient

@Configuration
class SecurityConfig(
    private val customAuthenticationFilter: CustomAuthenticationFilter,
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val oAuth2LoginSuccessHandler: OAuth2LoginSuccessHandler,
    private val oAuth2LoginFailureHandler: OAuth2LoginFailureHandler,
    private val authorizationRequestRepository: HttpCookieOAuth2AuthorizationRequestRepository
) {

    @Bean
    fun accessTokenResponseClient(): OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {
        val tokenResponseConverter = OAuth2AccessTokenResponseHttpMessageConverter().apply {
            setAccessTokenResponseConverter(CustomTokenResponseConverter())
        }

        val restClient = RestClient.builder()
            .configureMessageConverters { converters ->
                converters.addCustomConverter(FormHttpMessageConverter())
                converters.addCustomConverter(tokenResponseConverter)
            }
            .defaultStatusHandler(OAuth2ErrorResponseErrorHandler())
            .build()

        return RestClientAuthorizationCodeTokenResponseClient().apply {
            setRestClient(restClient)
        }
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/h2-console/**").permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/*/concerts",
                        "/api/*/concerts/*",
                        "/api/*/schedules/**",
                        "/api/*/users/check-id",
                        "/api/*/tickets/verify/*"
                    ).permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/*/auth/login",
                        "/api/*/auth/logout",
                        "/api/*/auth/refresh",
                        "/api/*/users/signup",
                        "/api/*/auth/restore"
                    ).permitAll()
                    .requestMatchers("/api/*/**").authenticated()
                    .anyRequest().permitAll()
            }
            .headers { headers ->
                headers.frameOptions { it.sameOrigin() }
            }
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .httpBasic { it.disable() }
            .sessionManagement { it.disable() }
            .addFilterBefore(customAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling { exceptionHandling ->
                exceptionHandling
                    .authenticationEntryPoint { _, response, _ ->
                        response.contentType = "application/json;charset=UTF-8"
                        response.status = 401
                        response.writer.write(
                            Ut.json.toString(
                                RsData<Void>(
                                    ErrorCode.AUTH_LOGIN_REQUIRED.resultCode,
                                    ErrorCode.AUTH_LOGIN_REQUIRED.message
                                ),
                                "{\"resultCode\":\"401-1\",\"msg\":\"로그인 후 이용해주세요.\",\"data\":null}"
                            )
                        )
                    }
                    .accessDeniedHandler { _, response, _ ->
                        response.contentType = "application/json;charset=UTF-8"
                        response.status = 403
                        response.writer.write(
                            Ut.json.toString(
                                RsData<Void>(
                                    ErrorCode.AUTH_FORBIDDEN.resultCode,
                                    ErrorCode.AUTH_FORBIDDEN.message
                                ),
                                "{\"resultCode\":\"403-1\",\"msg\":\"권한이 없습니다.\",\"data\":null}"
                            )
                        )
                    }
            }
            .oauth2Login { oauth2 ->
                oauth2
                    .authorizationEndpoint { authorization ->
                        authorization.authorizationRequestRepository(authorizationRequestRepository)
                    }
                    .tokenEndpoint { token ->
                        token.accessTokenResponseClient(accessTokenResponseClient())
                    }
                    .userInfoEndpoint { userInfo ->
                        userInfo.userService(customOAuth2UserService)
                    }
                    .successHandler(oAuth2LoginSuccessHandler)
                    .failureHandler(oAuth2LoginFailureHandler)
            }

        return http.build()
    }
}
