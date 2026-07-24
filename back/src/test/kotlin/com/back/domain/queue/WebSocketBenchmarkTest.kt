package com.back.domain.queue

import com.back.domain.queue.constant.QueueEventType
import com.back.domain.queue.dto.QueueEventResponse
import com.back.domain.queue.event.QueueStatusEvent
import com.back.global.RedisTestConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest
@Import(RedisTestConfig::class)
class WebSocketBenchmarkTest {
    @Autowired
    private lateinit var messagingTemplate: SimpMessagingTemplate

    @Test
    @DisplayName("유니캐스트 vs 브로드캐스트 성능 벤치마크")
    fun benchmarkUnicastVsBroadcast() {
        val userCount = 5000
        val scheduleId = 1L

        // 1. 유니캐스트 방식 측정
        val unicastStartTime = System.nanoTime()
        for (i in 1..userCount) {
            val event = QueueStatusEvent.of(scheduleId, i.toLong(), userCount.toLong())
            val response = QueueEventResponse.of(QueueEventType.QUEUE_STATUS_UPDATED, event)

            messagingTemplate.convertAndSendToUser(
                i.toString(),
                "/queue/schedules/$scheduleId/status",
                response
            )
        }
        val unicastEndTime = System.nanoTime()
        val unicastDurationMs = (unicastEndTime - unicastStartTime) / 1_000_000

        // 2. 브로드캐스트 방식 측정
        val broadcastStartTime = System.nanoTime()
        val broadcastEvent = QueueStatusEvent.of(scheduleId, 100L, userCount.toLong())
        val response = QueueEventResponse.of(QueueEventType.QUEUE_STATUS_UPDATED, broadcastEvent)

        messagingTemplate.convertAndSend(
            "/queue/schedules/$scheduleId/status",
            response
        )
        val broadcastEndTime = System.nanoTime()
        val broadcastDurationMs = (broadcastEndTime - broadcastStartTime) / 1_000_000

        println("WebSocket 전송 방식 성능 측정 결과 (사용자: ${userCount}명)")
        println("Unicast 소요 시간: $unicastDurationMs ms")
        println("Broadcast 소요 시간: $broadcastDurationMs ms")
    }
}
