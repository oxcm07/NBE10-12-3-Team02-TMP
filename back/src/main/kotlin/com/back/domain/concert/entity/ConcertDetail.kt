package com.back.domain.concert.entity

import com.back.global.jpa.entity.BaseEntity
import jakarta.persistence.*

@Entity
class ConcertDetail(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false)
    var concert: Concert,

    var urlDetail: String? = null
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val concertDetailId: Long? = null

    companion object {
        fun create(concert: Concert, urlDetail: String?): ConcertDetail {
            return ConcertDetail(
                concert = concert,
                urlDetail = urlDetail
            )
        }
    }
}
