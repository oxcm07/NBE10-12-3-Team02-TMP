package com.back.domain.concert.listener

import com.back.domain.concert.event.SeatExpiredEvent
import com.back.domain.concert.service.SeatOccupyManager
import com.back.domain.schedule.entity.SeatStatus
import com.back.domain.schedule.repository.ScheduleSeatRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class SeatHoldExpiredProcessor(
    private val scheduleSeatRepository: ScheduleSeatRepository,
    private val seatOccupyManager: SeatOccupyManager,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun processExpiredSeat(concertId: Long, scheduleId: Long, seatNumber: String) {
        log.debug("좌석 선점 만료 처리: concertId={}, scheduleId={}, seat={}", concertId, scheduleId, seatNumber)

        val seat = scheduleSeatRepository.findWithLockByScheduleIdAndSeatNumber(scheduleId, seatNumber)

        if (seat != null) {
            val wasHold = seat.seatStatus == SeatStatus.HOLD
            seat.releaseToAvailable()
            if (wasHold) {
                log.info("좌석 복구 완료 (HOLD → AVAILABLE): scheduleId={}, seat={}", scheduleId, seatNumber)
                eventPublisher.publishEvent(SeatExpiredEvent(concertId, scheduleId, seatNumber))
            } else {
                log.debug("좌석이 HOLD 상태가 아님 (이미 처리됨): scheduleId={}, seat={}, status={}",
                    scheduleId, seatNumber, seat.seatStatus)
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSeatExpiredCleanupRedis(event: SeatExpiredEvent) {
        val redisKey = SeatOccupyManager.generateSeatOccupyKey(
            event.concertId, event.scheduleId, event.seatNumber
        )
        val indexKey = SeatOccupyManager.generateSeatOccupyIndexKey(
            event.concertId, event.scheduleId
        )

        try {
            seatOccupyManager.cleanupRedis(redisKey, indexKey, event.seatNumber)
            log.debug("만료 좌석 Redis 정리 완료: {}", redisKey)
        } catch (e: Exception) {
            log.warn("만료 좌석 Redis 정리 실패 (무시됨): {}", redisKey, e)
        }
    }
}
