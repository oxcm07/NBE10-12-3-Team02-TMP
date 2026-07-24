package com.back.domain.concert.listener

import com.back.domain.concert.event.SeatOccupiedEvent
import org.slf4j.LoggerFactory
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.concurrent.TimeUnit

@Component
class SeatOccupiedEventListener(
    private val redissonClient: RedissonClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSeatOccupied(event: SeatOccupiedEvent) {
        val message = buildMessage(event.concertId, event.scheduleId, event.seatNumber)

        try {
            val blockingQueue = redissonClient.getBlockingQueue<String>(SeatHoldExpiredHandler.DELAYED_QUEUE_KEY)
            val delayedQueue = redissonClient.getDelayedQueue(blockingQueue)

            delayedQueue.offer(message, event.ttlSeconds, TimeUnit.SECONDS)

            log.debug("Delayed Queue 등록 완료: {}, TTL={}s", message, event.ttlSeconds)
        } catch (e: Exception) {
            log.warn("Delayed Queue 등록 실패 (좌석 TTL 만료 시 자동 복구됨): {}", message, e)
        }
    }

    companion object {
        @JvmStatic
        fun buildMessage(concertId: Long, scheduleId: Long, seatNumber: String): String {
            return "$concertId:$scheduleId:$seatNumber"
        }
    }
}
