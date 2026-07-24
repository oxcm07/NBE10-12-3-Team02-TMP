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
    concertName: String,
    description: String? = null,
    startDate: LocalDateTime,
    endDate: LocalDateTime,
    urlPoster: String? = null
) : BaseEntity() {

    @Column(nullable = false)
    var concertName: String = concertName
        protected set

    @Column(columnDefinition = "TEXT")
    var description: String? = description
        protected set

    @Column(nullable = false)
    var startDate: LocalDateTime = startDate
        protected set

    @Column(nullable = false)
    var endDate: LocalDateTime = endDate
        protected set

    var urlPoster: String? = urlPoster
        protected set

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val concertId: Long? = null

    fun isBookable(): Boolean = endDate.isAfter(LocalDateTime.now())

    companion object {
        fun create(
            concertName: String,
            description: String?,
            startDate: LocalDateTime,
            endDate: LocalDateTime,
            urlPoster: String?
        ): Concert = Concert(
            concertName = concertName,
            description = description,
            startDate = startDate,
            endDate = endDate,
            urlPoster = urlPoster
        )
    }
}
