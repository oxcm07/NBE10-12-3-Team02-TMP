package com.back.domain.concert.listener

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.redisson.api.RedissonClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty(name = ["seat.hold.handler.enabled"], havingValue = "true", matchIfMissing = true)
class SeatHoldExpiredHandler(
    private val redissonClient: RedissonClient,
    private val seatHoldExpiredProcessor: SeatHoldExpiredProcessor
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "seat-hold-expired-handler").apply { isDaemon = true }
    }

    @Volatile
    private var running = true

    @PostConstruct
    fun startListening() {
        executor.submit(this::listen)
        log.info("SeatHoldExpiredHandler 시작: Delayed Queue [{}] 감시 중", DELAYED_QUEUE_KEY)
    }

    @PreDestroy
    fun stopListening() {
        running = false
        executor.shutdownNow()
        log.info("SeatHoldExpiredHandler 종료")
    }

    private fun listen() {
        while (running) {
            try {
                val blockingQueue = redissonClient.getBlockingQueue<String>(DELAYED_QUEUE_KEY)
                val message = blockingQueue.poll(2, TimeUnit.SECONDS)
                if (message != null) {
                    handleMessage(message)
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                log.error("Delayed Queue 메시지 처리 중 오류: {}", e.message, e)
            }
        }
    }

    private fun handleMessage(message: String) {
        val parts = message.split(":")
        if (parts.size != 3) {
            log.warn("잘못된 Delayed Queue 메시지 형식: {}", message)
            return
        }

        try {
            val concertId = parts[0].toLong()
            val scheduleId = parts[1].toLong()
            val seatNumber = parts[2]

            seatHoldExpiredProcessor.processExpiredSeat(concertId, scheduleId, seatNumber)
        } catch (e: NumberFormatException) {
            log.warn("Delayed Queue 메시지 파싱 오류: {}", message, e)
        } catch (e: Exception) {
            log.error("좌석 만료 처리 실패: {}", message, e)
        }
    }

    companion object {
        const val DELAYED_QUEUE_KEY = "seat:hold:expired:queue"
    }
}
