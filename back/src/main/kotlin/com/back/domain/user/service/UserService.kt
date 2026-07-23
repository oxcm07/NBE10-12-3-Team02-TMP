package com.back.domain.user.service

import com.back.domain.schedule.entity.SeatStatus
import com.back.domain.ticket.event.TicketCancelledEvent
import com.back.domain.ticket.repository.TicketRepository
import com.back.domain.user.dto.*
import com.back.domain.user.entity.LoginType
import com.back.domain.user.entity.User
import com.back.domain.user.repository.UserRepository
import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import com.back.global.security.filter.BearerTokenExtractor
import com.back.global.security.jwt.JwtTokenProvider
import com.back.global.security.jwt.repository.BlacklistRepository
import com.back.global.security.jwt.repository.RefreshTokenRepository
import com.back.global.security.oauth2.service.OAuthUnlinkService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
    private val ticketRepository: TicketRepository,
    private val passwordEncoder: PasswordEncoder,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val blacklistRepository: BlacklistRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val bearerTokenExtractor: BearerTokenExtractor,
    private val oAuthUnlinkService: OAuthUnlinkService,
    private val eventPublisher: ApplicationEventPublisher,
    @Value("\${custom.jwt.blacklist.grace-seconds}") private val tokenBlacklistGraceSeconds: Long
) {

    @Transactional
    fun signup(request: SignupRequest): SignupResponse {
        val id = request.id
        val email = request.email
        val password = request.password
        val name = request.name

        if (userRepository.existsByLoginIdAndDeletedAtIsNull(id)) {
            throw ServiceException(ErrorCode.USER_ID_ALREADY_EXISTS)
        }
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw ServiceException(ErrorCode.USER_EMAIL_ALREADY_EXISTS)
        }
        val user = userRepository.save(
            User.create(
                loginId = id,
                email = email,
                password = passwordEncoder.encode(password),
                name = name,
                loginType = LoginType.NORMAL
            )
        )
        return SignupResponse.from(user)
    }

    @Transactional
    fun withdraw(userId: Long, authorization: String) {
        val accessToken = bearerTokenExtractor.extract(authorization)
        val user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
            .orElseThrow { ServiceException(ErrorCode.USER_NOT_FOUND_OR_DELETED) }

        if (user.loginType != LoginType.NORMAL) {
            oAuthUnlinkService.unlink(user.loginType, user.oauthRefreshToken)
        }

        val activeTickets = ticketRepository.findAllByUserWithConcert(user)
            .filter { it.isValid }

        for (ticket in activeTickets) {
            ticket.updateIsValid(false)
            ticket.scheduleSeat.updateSeatStatus(SeatStatus.AVAILABLE)

            val concertId = ticket.schedule.concert.concertId
                ?: throw IllegalStateException("Concert ID is null")
            val scheduleId = ticket.schedule.scheduleId
                ?: throw IllegalStateException("Schedule ID is null")

            eventPublisher.publishEvent(
                TicketCancelledEvent(
                    concertId = concertId,
                    scheduleId = scheduleId,
                    userId = userId
                )
            )
        }

        user.withdraw()
        refreshTokenRepository.deleteAllByUserId(userId)
        val remaining = jwtTokenProvider.getRemainingSeconds(accessToken)
        blacklistRepository.add(accessToken, Duration.ofSeconds(remaining + tokenBlacklistGraceSeconds))
    }

    fun getMyPage(userId: Long): MyPageResponse {
        val user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
            .orElseThrow { ServiceException(ErrorCode.USER_NOT_FOUND) }

        val ticketGroups = ticketRepository.findAllByUserWithConcert(user)
            .groupBy { it.schedule.scheduleId }
            .values
            .map { TicketGroupInfo.from(it) }

        return MyPageResponse.from(user, ticketGroups)
    }

    @Transactional
    fun updateMyPage(userId: Long, request: UpdateMyPageRequest) {
        val user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
            .orElseThrow { ServiceException(ErrorCode.USER_NOT_FOUND) }

        val name = request.name
        if (name != null) {
            val trimmed = name.trim()
            if (trimmed.isEmpty() || trimmed.contains(" ")) {
                throw ServiceException(ErrorCode.USER_NAME_INVALID)
            }
            user.updateName(trimmed)
        }

        val email = request.email
        if (email != null) {
            if (user.email != email && userRepository.existsByEmailAndDeletedAtIsNull(email)) {
                throw ServiceException(ErrorCode.USER_EMAIL_ALREADY_EXISTS)
            }
            user.updateEmail(email)
        }

        val password = request.password
        if (password != null) {
            user.updatePassword(passwordEncoder.encode(password))
        }
    }

    fun checkId(id: String) {
        if (userRepository.existsByLoginIdAndDeletedAtIsNull(id)) {
            throw ServiceException(ErrorCode.USER_ID_ALREADY_EXISTS)
        }
    }
}
