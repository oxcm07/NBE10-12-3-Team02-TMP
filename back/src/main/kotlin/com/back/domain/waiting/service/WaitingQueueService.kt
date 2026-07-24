package com.back.domain.waiting.service

import com.back.domain.concert.service.ConcertService
import com.back.domain.queue.event.EntryAllowedEvent
import com.back.domain.queue.event.QueueErrorEvent
import com.back.domain.queue.event.QueueStatusEvent
import com.back.domain.schedule.entity.SeatStatus
import com.back.domain.schedule.repository.ScheduleSeatRepository
import com.back.domain.user.repository.UserRepository
import com.back.domain.waiting.dto.WaitingQueueResponse
import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class WaitingQueueService(
    private val waitingQueueManager: WaitingQueueManager,
    private val userRepository: UserRepository,
    private val concertService: ConcertService,
    private val eventPublisher: ApplicationEventPublisher,
    private val scheduleSeatRepository: ScheduleSeatRepository,
    @Value("\${queue.entry-token.ttl}") private val entryTokenTtl: Duration,
    @Value("\${queue.batch-size}") private val batchSize: Int,
    @Value("\${queue.max-active-users}") private val maxActiveUsers: Int
) {

    fun registerWaiting(concertId: Long, scheduleId: Long, userId: Long): WaitingQueueResponse {
        validateUser(userId)
        concertService.validateConcertScheduleMatch(concertId, scheduleId)
        concertService.validateScheduleBookable(scheduleId)

        val remainingSeats = scheduleSeatRepository.countBySchedule_ScheduleIdAndSeatStatusIn(
            scheduleId,
            listOf(SeatStatus.AVAILABLE, SeatStatus.HOLD)
        )
        if (remainingSeats <= 0) {
            throw ServiceException(ErrorCode.CONCERT_SOLD_OUT)
        }

        waitingQueueManager.getActiveToken(scheduleId, userId)?.let { activeToken ->
            return WaitingQueueResponse.of(concertId, scheduleId, userId, 0L, 0L, activeToken)
        }

        val rank = waitingQueueManager.registerWaiting(scheduleId, userId)
        val myQueueNumber = waitingQueueManager.getQueueSequence(scheduleId, userId)

        allowEntry(concertId, scheduleId)

        return WaitingQueueResponse.of(
            concertId,
            scheduleId,
            userId,
            rank,
            myQueueNumber,
            null
        )
    }

    fun showWaitingRank(concertId: Long, scheduleId: Long, userId: Long): WaitingQueueResponse {
        validateUser(userId)
        concertService.validateConcertScheduleMatch(concertId, scheduleId)

        waitingQueueManager.getActiveToken(scheduleId, userId)?.let { activeToken ->
            return WaitingQueueResponse.of(concertId, scheduleId, userId, 0L, 0L, activeToken)
        }

        val rank = waitingQueueManager.showWaitingRank(scheduleId, userId)
        val myQueueNumber = waitingQueueManager.getQueueSequence(scheduleId, userId)

        return WaitingQueueResponse.of(
            concertId,
            scheduleId,
            userId,
            rank,
            myQueueNumber,
            null
        )
    }

    fun cancelWaiting(concertId: Long, scheduleId: Long, userId: Long) {
        validateUser(userId)
        concertService.validateConcertScheduleMatch(concertId, scheduleId)

        val removedFromWaiting = waitingQueueManager.cancelWaiting(scheduleId, userId)
        val removedFromActive = !removedFromWaiting && waitingQueueManager.cancelActiveUser(scheduleId, userId)

        if (!removedFromWaiting && !removedFromActive) {
            throw ServiceException(ErrorCode.WAITING_QUEUE_NOT_FOUND)
        }

        if (removedFromActive) {
            allowEntry(concertId, scheduleId)
        } else {
            publishQueueRank(scheduleId)
        }
    }

    fun allowEntry(concertId: Long, scheduleId: Long) {
        concertService.validateConcertScheduleMatch(concertId, scheduleId)

        val remainingSeats = scheduleSeatRepository.countBySchedule_ScheduleIdAndSeatStatus(
            scheduleId, SeatStatus.AVAILABLE
        )

        if (remainingSeats <= 0) {
            val status = waitingQueueManager.getQueueStatus(scheduleId)
            if (status.totalWaitingCount > 0) {
                eventPublisher.publishEvent(
                    QueueErrorEvent(scheduleId, null, "콘서트가 매진되어 대기열이 종료되었습니다.")
                )
                waitingQueueManager.clearWaitingQueue(scheduleId)
            }
            return
        }

        val capacity = minOf(remainingSeats, maxActiveUsers.toLong())
        val userIds = waitingQueueManager.addActiveUser(scheduleId, capacity, batchSize, entryTokenTtl)

        for (userId in userIds) {
            val entryToken = waitingQueueManager.issueToken(scheduleId, userId, entryTokenTtl)
            val expiredAt = System.currentTimeMillis() + entryTokenTtl.toMillis()

            eventPublisher.publishEvent(
                EntryAllowedEvent(scheduleId, userId, entryToken, expiredAt)
            )
        }
        publishQueueRank(scheduleId)
    }

    private fun publishQueueRank(scheduleId: Long) {
        val status = waitingQueueManager.getQueueStatus(scheduleId)

        if (status.totalWaitingCount == 0L) return

        eventPublisher.publishEvent(
            QueueStatusEvent.of(
                scheduleId,
                status.currentAllowedSequence,
                status.totalWaitingCount
            )
        )
    }

    private fun validateUser(userId: Long) {
        userRepository.findByUserIdAndDeletedAtIsNull(userId)
            ?: throw ServiceException(ErrorCode.USER_NOT_FOUND)
    }
}
