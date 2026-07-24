package com.back.domain.waiting.dto

data class QueueStatusDto(
    val currentAllowedSequence: Long,
    val totalWaitingCount: Long
)
