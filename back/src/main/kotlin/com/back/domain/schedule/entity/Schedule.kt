package com.back.domain.schedule.entity

import com.back.domain.concert.entity.Concert
import com.back.domain.venue.entity.Venue
import com.back.global.jpa.entity.BaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
open class Schedule protected constructor() : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var scheduleId: Long? = null
        private set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false)
    lateinit var concert: Concert
        private set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    lateinit var venue: Venue
        private set

    @Column(nullable = false)
    lateinit var scheduleDate: LocalDateTime
        private set

    @Column(nullable = false)
    var round: Int = 0
        private set

    private constructor(
        concert: Concert,
        venue: Venue,
        scheduleDate: LocalDateTime,
        round: Int
    ) : this() {
        this.concert = concert
        this.venue = venue
        this.scheduleDate = scheduleDate
        this.round = round
    }

    companion object {
        @JvmStatic
        fun create(concert: Concert, venue: Venue, scheduleDate: LocalDateTime, round: Int): Schedule =
            Schedule(concert, venue, scheduleDate, round)
    }
}
