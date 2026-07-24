package com.back.domain.concert.sse

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class SeatStatusSseEmitterRegistry {
    private val log = LoggerFactory.getLogger(javaClass)

    private val emitters = ConcurrentHashMap<Long, MutableList<SseEmitter>>()

    fun register(scheduleId: Long, emitter: SseEmitter): SseEmitter {
        emitters.computeIfAbsent(scheduleId) { CopyOnWriteArrayList() }.add(emitter)

        val cleanup = Runnable {
            val list = emitters[scheduleId]
            list?.remove(emitter)
        }
        emitter.onCompletion(cleanup)
        emitter.onTimeout(cleanup)
        emitter.onError { cleanup.run() }

        log.debug("SSE 구독 등록: scheduleId={}, 총 구독자={}", scheduleId, emitters[scheduleId]?.size ?: 0)

        return emitter
    }

    fun broadcast(scheduleId: Long, seatNumber: String, status: String) {
        val list = emitters[scheduleId]
        if (list.isNullOrEmpty()) return

        val data = "{\"seatNumber\":\"$seatNumber\",\"status\":\"$status\"}"

        for (emitter in list) {
            try {
                emitter.send(
                    SseEmitter.event()
                        .name("seat_status_changed")
                        .data(data)
                )
            } catch (e: IOException) {
                log.debug("SSE 전송 실패 (연결 끊김): scheduleId={}, seat={}", scheduleId, seatNumber)
                emitter.complete()
            }
        }
    }
}
