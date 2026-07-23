package com.back.domain.concert.controller

import com.back.domain.concert.sse.SeatStatusSseEmitterRegistry
import com.back.domain.schedule.repository.ScheduleSeatRepository
import com.back.global.annotation.ApiV1
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException

@ApiV1
@RestController
@RequestMapping("/concerts")
@Tag(name = "Concert SSE", description = "실시간 좌석 상태 SSE API")
class SeatStatusSseController(
    private val registry: SeatStatusSseEmitterRegistry,
    private val scheduleSeatRepository: ScheduleSeatRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping(
        value = ["/{concertId}/schedules/{scheduleId}/seats/status"],
        produces = ["text/event-stream;charset=UTF-8"]
    )
    @Operation(
        summary = "실시간 좌석 상태 SSE 스트림",
        description = "좌석 상태 변경 이벤트(선점/만료/결제완료)를 실시간으로 수신합니다."
    )
    fun seatStatusStream(
        @PathVariable concertId: Long,
        @PathVariable scheduleId: Long
    ): SseEmitter {
        val emitter = SseEmitter(SSE_TIMEOUT_MS)
        registry.register(scheduleId, emitter)

        try {
            val seats = scheduleSeatRepository.findByScheduleScheduleId(scheduleId)
            val snapshot = StringBuilder("[")
            for (i in seats.indices) {
                val seat = seats[i]
                snapshot.append("{\"seatNumber\":\"")
                    .append(seat.seatNumber)
                    .append("\",\"status\":\"")
                    .append(seat.seatStatus.name)
                    .append("\"}")
                if (i < seats.size - 1) snapshot.append(",")
            }
            snapshot.append("]")

            emitter.send(
                SseEmitter.event()
                    .name("seat_snapshot")
                    .data(snapshot.toString())
            )
        } catch (e: IOException) {
            log.warn("SSE 스냅샷 전송 실패: scheduleId={}", scheduleId, e)
            emitter.complete()
        }

        log.debug("SSE 스트림 연결: concertId={}, scheduleId={}", concertId, scheduleId)
        return emitter
    }

    companion object {
        private const val SSE_TIMEOUT_MS = 30 * 60 * 1000L // 30분
    }
}
