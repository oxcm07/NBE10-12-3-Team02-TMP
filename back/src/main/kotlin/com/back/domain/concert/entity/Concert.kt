package com.back.domain.concert.entity

import com.back.global.jpa.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDateTime

@Entity
class Concert(
    @Column(nullable = false)
    var concertName: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(nullable = false)
    var startDate: LocalDateTime,

    @Column(nullable = false)
    var endDate: LocalDateTime,

    var urlPoster: String? = null
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val concertId: Long? = null

    companion object {
        fun create(
            concertName: String,
            description: String?,
            startDate: LocalDateTime,
            endDate: LocalDateTime,
            urlPoster: String?
        ): Concert {
            return Concert(
                concertName = concertName,
                description = description,
                startDate = startDate,
                endDate = endDate,
                urlPoster = urlPoster
            )
        }
    }
}
