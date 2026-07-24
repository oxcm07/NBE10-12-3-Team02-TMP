package com.back.domain.user.controller

import com.back.domain.user.dto.MyPageResponse
import com.back.domain.user.dto.SignupRequest
import com.back.domain.user.dto.SignupResponse
import com.back.domain.user.dto.UpdateMyPageRequest
import com.back.domain.user.service.UserService
import com.back.global.annotation.ApiV1
import com.back.global.requestcontext.RequestContext
import com.back.global.rsData.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@ApiV1
@RestController
@RequestMapping("/users")
@Tag(name = "User", description = "User API")
class UserController(
    private val userService: UserService,
    private val requestContext: RequestContext
) {

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "회원가입 API")
    fun signup(@RequestBody @Valid request: SignupRequest): RsData<SignupResponse> {
        return RsData("200-1", "회원가입이 완료되었습니다.", userService.signup(request))
    }

    @PatchMapping("/withdraw")
    @Operation(summary = "회원 탈퇴", description = "회원 탈퇴 API")
    fun withdraw(@RequestHeader("Authorization") authorization: String): RsData<Void> {
        val actor = requestContext.actor ?: throw IllegalStateException("Actor must not be null")
        userService.withdraw(actor.id, authorization)
        requestContext.deleteCookie("refreshToken", "/api/v1/auth")
        return RsData("200-1", "회원 탈퇴가 정상적으로 완료되었습니다.", null)
    }

    @GetMapping("/me")
    @Operation(summary = "마이페이지 조회", description = "마이페이지 조회 API")
    fun getMyPage(): RsData<MyPageResponse> {
        val actor = requestContext.actor ?: throw IllegalStateException("Actor must not be null")
        return RsData("200-1", "마이페이지 조회 성공", userService.getMyPage(actor.id))
    }

    @PatchMapping("/me")
    @Operation(summary = "마이페이지 수정", description = "마이페이지 수정 API")
    fun updateMyPage(@RequestBody @Valid request: UpdateMyPageRequest): RsData<Void> {
        val actor = requestContext.actor ?: throw IllegalStateException("Actor must not be null")
        userService.updateMyPage(actor.id, request)
        return RsData("200-1", "마이페이지 수정 성공", null)
    }

    @GetMapping("/check-id")
    @Operation(summary = "아이디 중복확인", description = "아이디 중복확인 API")
    fun checkId(@RequestParam id: String): RsData<Void> {
        userService.checkId(id)
        return RsData("200-1", "사용 가능한 아이디입니다.", null)
    }
}
