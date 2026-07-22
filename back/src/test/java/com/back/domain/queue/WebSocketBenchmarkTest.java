package com.back.domain.queue;

import com.back.domain.queue.constant.QueueEventType;
import com.back.domain.queue.dto.QueueEventResponse;
import com.back.domain.queue.event.QueueStatusEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@org.springframework.context.annotation.Import(com.back.global.RedisTestConfig.class)
class WebSocketBenchmarkTest {
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("유니캐스트 vs 브로드캐스트 성능 벤치마크")
    void benchmarkUnicastVsBroadcast() {
        int userCount = 5000;
        Long scheduleId = 1L;

        // 1. 유니캐스트 방식 측정
        long unicastStartTime = System.nanoTime();
        for (int i = 1; i <= userCount; i++) {
            QueueStatusEvent event = QueueStatusEvent.of(scheduleId, (long) i, (long) userCount);
            QueueEventResponse<QueueStatusEvent> response =
                    QueueEventResponse.of(QueueEventType.QUEUE_STATUS_UPDATED, event);

            messagingTemplate.convertAndSendToUser(
                    String.valueOf(i),
                    "/queue/schedules/%d/status".formatted(scheduleId),
                    response
            );
        }
        long unicastEndTime = System.nanoTime();
        long unicastDurationMs = (unicastEndTime - unicastStartTime) / 1_000_000;

        // 2. 브로드캐스트 방식 측정
        long broadcastStartTime = System.nanoTime();
        QueueStatusEvent broadcastEvent = QueueStatusEvent.of(scheduleId, 100L, (long) userCount);
        QueueEventResponse<QueueStatusEvent> response =
                QueueEventResponse.of(QueueEventType.QUEUE_STATUS_UPDATED, broadcastEvent);

        messagingTemplate.convertAndSend(
                "/queue/schedules/%d/status".formatted(scheduleId),
                response
        );
        long broadcastEndTime = System.nanoTime();
        long broadcastDurationMs = (broadcastEndTime - broadcastStartTime) / 1_000_000;

        System.out.println("WebSocket 전송 방식 성능 측정 결과 (사용자: " + userCount + "명)");
        System.out.println("Unicast 소요 시간: " + unicastDurationMs + " ms");
        System.out.println("Broadcast 소요 시간: " + broadcastDurationMs + " ms");
    }
}
