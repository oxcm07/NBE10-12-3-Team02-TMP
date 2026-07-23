package com.back.domain.ticket.service

import com.back.domain.concert.listener.SeatHoldExpiredHandler
import com.back.domain.concert.listener.SeatOccupiedEventListener
import com.back.domain.concert.service.SeatOccupyManager
import com.back.domain.concert.sse.SeatStatusSseEmitterRegistry
import com.back.domain.schedule.entity.ScheduleSeat
import com.back.domain.schedule.entity.SeatStatus
import com.back.domain.schedule.repository.ScheduleRepository
import com.back.domain.schedule.repository.ScheduleSeatRepository
import com.back.domain.ticket.dto.PaymentTicketRequest
import com.back.domain.ticket.dto.PaymentTicketResponse
import com.back.domain.ticket.dto.SeatHoldInfo
import com.back.domain.ticket.dto.TicketVerifyResponse
import com.back.domain.ticket.entity.Ticket
import com.back.domain.ticket.event.PaymentCompletedEvent
import com.back.domain.ticket.event.TicketCancelledEvent
import com.back.domain.ticket.repository.TicketRepository
import com.back.domain.user.repository.UserRepository
import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import org.redisson.api.RedissonClient
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class TicketService(
    private val ticketRepository: TicketRepository,
    private val userRepository: UserRepository,
    private val scheduleRepository: ScheduleRepository,
    private val scheduleSeatRepository: ScheduleSeatRepository,
    private val redissonClient: RedissonClient,
    private val eventPublisher: ApplicationEventPublisher,
    private val seatOccupyManager: SeatOccupyManager,
    private val sseEmitterRegistry: SeatStatusSseEmitterRegistry
) {

    @Transactional
    fun createTicket(userId: Long, scheduleId: Long, request: PaymentTicketRequest): List<PaymentTicketResponse> {
        val user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
            ?: throw ServiceException(ErrorCode.USER_NOT_FOUND)

        val schedule = scheduleRepository
            .findByScheduleIdAndConcert_ConcertId(scheduleId, request.concertId)
            ?: throw ServiceException(ErrorCode.INVALID_CONCERT_SCHEDULE)

        val alreadyPurchasedCount = ticketRepository
            .countByUser_UserIdAndSchedule_ScheduleIdAndIsValidTrue(userId, scheduleId)
        if (alreadyPurchasedCount + request.seatHolds.size > 3) {
            throw ServiceException(ErrorCode.EXCEED_TICKET_LIMIT)
        }

        val sortedSeatHolds = request.seatHolds.sortedBy { it.seatNumber }

        val scheduleSeats = mutableListOf<ScheduleSeat>()
        for (holdInfo in sortedSeatHolds) {
            val scheduleSeat = scheduleSeatRepository
                .findWithLockByScheduleIdAndSeatNumber(scheduleId, holdInfo.seatNumber)
                ?: throw ServiceException(ErrorCode.SEAT_NOT_FOUND)

            if (scheduleSeat.seatStatus == SeatStatus.SOLD_OUT) {
                throw ServiceException(ErrorCode.SEAT_ALREADY_SOLD)
            }
            if (scheduleSeat.seatStatus != SeatStatus.HOLD) {
                throw ServiceException(ErrorCode.SEAT_HOLD_EXPIRED)
            }

            scheduleSeats.add(scheduleSeat)
        }

        validateSeatHold(userId, request.concertId, scheduleId, sortedSeatHolds)

        scheduleSeats.forEach { it.updateSeatStatus(SeatStatus.SOLD_OUT) }

        for (holdInfo in sortedSeatHolds) {
            val redisKey = SeatOccupyManager.generateSeatOccupyKey(
                request.concertId, scheduleId, holdInfo.seatNumber
            )
            val indexKey = SeatOccupyManager.generateSeatOccupyIndexKey(
                request.concertId, scheduleId
            )
            seatOccupyManager.cleanupRedis(redisKey, indexKey, holdInfo.seatNumber)
            cancelDelayedQueueMessage(request.concertId, scheduleId, holdInfo.seatNumber)
            sseEmitterRegistry.broadcast(scheduleId, holdInfo.seatNumber, SeatStatus.SOLD_OUT.name)
        }

        val tickets = scheduleSeats.map { seat ->
            Ticket.create(user, schedule, seat, createTicketNumber(), seat.seatPrice)
        }
        ticketRepository.saveAll(tickets)

        eventPublisher.publishEvent(
            PaymentCompletedEvent(request.concertId, scheduleId, userId)
        )

        return tickets.indices.map { i ->
            PaymentTicketResponse.from(scheduleSeats[i], schedule, tickets[i])
        }
    }

    @Transactional
    fun cancelTicket(userId: Long, ticketId: Long) {
        val ticket = ticketRepository.findByTicketIdAndUser_UserId(ticketId, userId)
            ?: throw ServiceException(ErrorCode.TICKET_NOT_FOUND_FOR_USER)

        if (!ticket.isValid) {
            throw ServiceException(ErrorCode.TICKET_ALREADY_CANCELLED)
        }

        ticket.updateIsValid(false)
        ticket.scheduleSeat.updateSeatStatus(SeatStatus.AVAILABLE)

        val concertId = ticket.schedule.concert.concertId
            ?: throw IllegalStateException("Concert ID is null")
        val scheduleId = ticket.schedule.scheduleId
            ?: throw IllegalStateException("Schedule ID is null")
        val seatNumber = ticket.scheduleSeat.seatNumber

        val redisKey = SeatOccupyManager.generateSeatOccupyKey(concertId, scheduleId, seatNumber)
        val indexKey = SeatOccupyManager.generateSeatOccupyIndexKey(concertId, scheduleId)
        seatOccupyManager.cleanupRedis(redisKey, indexKey, seatNumber)

        cancelDelayedQueueMessage(concertId, scheduleId, seatNumber)
        sseEmitterRegistry.broadcast(scheduleId, seatNumber, SeatStatus.AVAILABLE.name)

        eventPublisher.publishEvent(TicketCancelledEvent(concertId, scheduleId, userId))
    }

    fun verifyTicket(qrToken: String): TicketVerifyResponse {
        val ticket = ticketRepository.findByQrTokenWithDetails(qrToken)
            ?: throw ServiceException(ErrorCode.TICKET_NOT_FOUND)
        return TicketVerifyResponse.from(ticket)
    }

    fun createTicketNumber(): String = UUID.randomUUID().toString()

    private fun validateSeatHold(userId: Long, concertId: Long, scheduleId: Long, seatHolds: List<SeatHoldInfo>) {
        for (hold in seatHolds) {
            val redisKey = SeatOccupyManager.generateSeatOccupyKey(concertId, scheduleId, hold.seatNumber)
            val hashMap = redissonClient.getMap<String, String>(redisKey)

            val holdUserId = hashMap["userId"]
            val holdOccupyToken = hashMap["occupyToken"]

            if (holdUserId == null || holdOccupyToken == null) {
                throw ServiceException(ErrorCode.SEAT_HOLD_EXPIRED)
            }
            if (userId.toString() != holdUserId) {
                throw ServiceException(ErrorCode.SEAT_HELD_BY_OTHER_USER)
            }
            if (hold.occupyToken != holdOccupyToken) {
                throw ServiceException(ErrorCode.INVALID_OCCUPY_TOKEN)
            }
        }
    }

    private fun cancelDelayedQueueMessage(concertId: Long, scheduleId: Long, seatNumber: String) {
        try {
            val message = SeatOccupiedEventListener.buildMessage(concertId, scheduleId, seatNumber)
            val blockingQueue = redissonClient.getBlockingQueue<String>(SeatHoldExpiredHandler.DELAYED_QUEUE_KEY)
            val delayedQueue = redissonClient.getDelayedQueue(blockingQueue)
            delayedQueue.remove(message)
        } catch (ignored: Exception) {
        }
    }
}
