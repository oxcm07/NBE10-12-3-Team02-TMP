package com.back.domain.queue.listener

import com.back.domain.queue.constant.QueueEventType
import com.back.domain.queue.dto.QueueEventResponse
import com.back.domain.queue.event.EntryAllowedEvent
import com.back.domain.queue.event.QueueErrorEvent
import com.back.domain.queue.event.QueueStatusEvent
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
@Async
class QueueEntryEventHandler(
    private val messagingTemplate: SimpMessagingTemplate
) {

    @EventListener
    fun handleQueueStatusUpdated(event: QueueStatusEvent) {
        val response = QueueEventResponse.of(QueueEventType.QUEUE_STATUS_UPDATED, event)
        messagingTemplate.convertAndSend("/queue/schedules/${event.scheduleId}/status", response)
    }

    @EventListener
    fun handleEntryAllowed(event: EntryAllowedEvent) {
        val response = QueueEventResponse.of(QueueEventType.ENTRY_ALLOWED, event)
        messagingTemplate.convertAndSendToUser(
            event.userId.toString(),
            "/queue/schedules/${event.scheduleId}/entry",
            response
        )
    }

    @EventListener
    fun handleQueueError(event: QueueErrorEvent) {
        val response = QueueEventResponse.of(QueueEventType.QUEUE_ERROR, event)

        if (event.userId != null) {
            messagingTemplate.convertAndSendToUser(
                event.userId.toString(),
                "/queue/schedules/${event.scheduleId}/entry",
                response
            )
        } else {
            messagingTemplate.convertAndSend("/queue/schedules/${event.scheduleId}/status", response)
        }
    }
}
