package com.back.domain.queue.event

data class EntryAllowedEvent(
    val scheduleId: Long,
    val userId: Long,
    val entryToken: String,
    val expiredAt: Long
)
