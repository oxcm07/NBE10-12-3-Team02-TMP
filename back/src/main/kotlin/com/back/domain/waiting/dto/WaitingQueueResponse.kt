package com.back.domain.waiting.dto

data class WaitingQueueResponse(
    val concertId: Long,
    val scheduleId: Long,
    val userId: Long,
    val rank: Long,
    val myQueueNumber: Long,
    val entryToken: String?
) {
    companion object {
        fun of(
            concertId: Long,
            scheduleId: Long,
            userId: Long,
            rank: Long,
            myQueueNumber: Long,
            entryToken: String?
        ): WaitingQueueResponse {
            return WaitingQueueResponse(concertId, scheduleId, userId, rank, myQueueNumber, entryToken)
        }
    }
}
