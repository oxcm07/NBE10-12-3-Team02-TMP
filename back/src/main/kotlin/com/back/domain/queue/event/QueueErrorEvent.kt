package com.back.domain.queue.event

data class QueueErrorEvent(
    val scheduleId: Long,
    val userId: Long?,
    val errorMessage: String
)
