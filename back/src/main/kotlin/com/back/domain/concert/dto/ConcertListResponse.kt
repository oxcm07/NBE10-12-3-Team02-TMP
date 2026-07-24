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
        fun of(concert: Concert, venueName: String): ConcertListResponse = ConcertListResponse(
            concertId = checkNotNull(concert.concertId) { "Concert ID null" },
            concertName = concert.concertName,
            venueName = venueName,
            startDate = concert.startDate,
            endDate = concert.endDate,
            imageUrl = concert.urlPoster,
            status = if (concert.endDate.isAfter(LocalDateTime.now())) "AVAILABLE" else "CLOSED"
        )
    }
}
