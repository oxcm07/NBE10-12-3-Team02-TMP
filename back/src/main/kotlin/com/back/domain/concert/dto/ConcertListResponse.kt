package com.back.domain.concert.dto

import com.back.domain.concert.entity.Concert
import java.time.LocalDateTime

data class ConcertListResponse(
    val concertId: Long,
    val concertName: String,
    val venueName: String,
    val startDate: LocalDateTime,
    val endDate: LocalDateTime,
    val imageUrl: String?,
    val status: String
) {
    companion object {
        fun of(concert: Concert, venueName: String): ConcertListResponse {
            val concertId = concert.concertId ?: throw IllegalArgumentException("Concert ID null")
            val status = if (concert.endDate.isAfter(LocalDateTime.now())) "AVAILABLE" else "CLOSED"
            return ConcertListResponse(
                concertId = concertId,
                concertName = concert.concertName,
                venueName = venueName,
                startDate = concert.startDate,
                endDate = concert.endDate,
                imageUrl = concert.urlPoster,
                status = status
            )
        }
    }
}
