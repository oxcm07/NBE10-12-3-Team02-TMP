package com.back.domain.queue.event

import java.time.LocalDateTime

data class QueueStatusEvent(
    val scheduleId: Long,
    val currentRank: Long,
    val totalWaitingCount: Long,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun of(scheduleId: Long, currentAllowedSequence: Long, totalWaitingCount: Long): QueueStatusEvent {
            return QueueStatusEvent(
                scheduleId = scheduleId,
                currentRank = currentAllowedSequence,
                totalWaitingCount = totalWaitingCount,
                updatedAt = LocalDateTime.now()
            )
        }
    }
}
