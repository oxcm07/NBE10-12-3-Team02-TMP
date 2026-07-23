package com.back.domain.concert.dto

import com.back.domain.concert.entity.Concert

data class ConcertDetailResponse(
    val concertId: Long,
    val concertName: String,
    val description: String?,
    val venueName: String,
    val location: String,
    val urlPoster: String?,
    val detailUrlList: List<String>,
    val prices: Map<String, Int>,
    val bookable: Boolean
) {
    companion object {
        fun of(
            concert: Concert,
            venueName: String,
            location: String,
            detailUrlList: List<String>,
            prices: Map<String, Int>,
            bookable: Boolean
        ): ConcertDetailResponse {
            val concertId = concert.concertId ?: throw IllegalArgumentException("Concert ID null")
            return ConcertDetailResponse(
                concertId = concertId,
                concertName = concert.concertName,
                description = concert.description,
                venueName = venueName,
                location = location,
                urlPoster = concert.urlPoster,
                detailUrlList = detailUrlList,
                prices = prices,
                bookable = bookable
            )
        }
    }
}
