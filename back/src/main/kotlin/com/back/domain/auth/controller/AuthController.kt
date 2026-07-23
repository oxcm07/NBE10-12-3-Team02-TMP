package com.back.domain.auth.controller

import com.back.domain.auth.dto.AuthRestoreResponse
import com.back.domain.auth.dto.LoginRequest
import com.back.domain.auth.service.AuthService
import com.back.global.annotation.ApiV1
import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import com.back.global.requestcontext.RequestContext
import com.back.global.rsData.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

@ApiV1
@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Auth API")
class AuthController(
    private val requestContext: RequestContext,
    private val authService: AuthService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "로그인 API")
    fun login(@RequestBody @Valid request: LoginRequest): RsData<Void> {
        val tokenResponse = authService.login(request.id, request.password)

        val accessToken = tokenResponse.accessToken
        val refreshToken = tokenResponse.refreshToken

        requestContext.setCookie("refreshToken", refreshToken, "/api/v1/auth")
        requestContext.setHeader("Authorization", "Bearer $accessToken")

        return RsData(
            "200-1",
            "로그인 성공 및 인증 토큰이 발급되었습니다."
        )
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "로그아웃 API")
    fun logout(@RequestHeader(value = "Authorization", required = false) authorization: String?): RsData<Void> {
        val refreshToken = requestContext.getCookieValue("refreshToken", "")
        authService.logout(refreshToken, authorization)

        requestContext.deleteCookie("refreshToken", "/api/v1/auth")

        return RsData(
            "200-1",
            "로그아웃이 완료되었습니다. 토큰 및 세션 정보가 무효화되었습니다."
        )
    }

    @PostMapping("/refresh")
    @Operation(summary = "토큰 재발급", description = "토큰 재발급 API")
    fun refresh(): RsData<Void> {
        val refreshToken = requestContext.getCookieValue("refreshToken", "")

        if (refreshToken.isBlank()) {
            throw ServiceException(ErrorCode.AUTH_LOGIN_REQUIRED)
        }

        val tokenResponse = authService.refresh(refreshToken)

        requestContext.setCookie("refreshToken", tokenResponse.refreshToken, "/api/v1/auth")
        requestContext.setHeader("Authorization", "Bearer ${tokenResponse.accessToken}")

        return RsData(
            "200-1",
            "Access Token이 정상적으로 재발급되었습니다."
        )
    }

    @PostMapping("/restore")
    @Operation(summary = "새로고침", description = "새로고침 API")
    fun restore(): RsData<AuthRestoreResponse> {
        val refreshToken = requestContext.getCookieValue("refreshToken", "")

        return try {
            val accessToken = authService.restore(refreshToken)
            requestContext.setHeader("Authorization", "Bearer $accessToken")

            RsData("200-2", "로그인 상태가 복구되었습니다.", AuthRestoreResponse(true))
        } catch (e: ServiceException) {
            if (e.errorCode == ErrorCode.AUTH_REFRESH_TOKEN_ROTATION_FAILED) {
                throw e
            }

            if (e.errorCode == ErrorCode.AUTH_REFRESH_TOKEN_MISMATCH) {
                log.warn(
                    "Refresh token mismatch detected during restore. clientIp={}, userAgent={}",
                    requestContext.clientIp,
                    requestContext.getHeader("User-Agent", "")
                )
                throw e
            }

            requestContext.deleteCookie("refreshToken", "/api/v1/auth")
            RsData("200-1", "비로그인 상태입니다.", AuthRestoreResponse(false))
        }
    }
}
