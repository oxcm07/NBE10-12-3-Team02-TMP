package com.back.domain.queue.dto

import com.back.domain.queue.constant.QueueEventType

data class QueueEventResponse<T>(
    val eventType: QueueEventType,
    val data: T
) {
    companion object {
        fun <T> of(eventType: QueueEventType, data: T): QueueEventResponse<T> {
            return QueueEventResponse(eventType, data)
        }
    }
}
