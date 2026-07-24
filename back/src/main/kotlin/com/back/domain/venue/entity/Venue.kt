package com.back.domain.venue.entity

import com.back.global.jpa.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity
class Venue(
    @Column(nullable = false)
    var venueName: String,

    @Column(nullable = false)
    var location: String,

    @Column(nullable = false)
    var totalSeats: Long
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val venueId: Long? = null

    companion object {
        fun create(venueName: String, location: String, totalSeats: Long): Venue {
            return Venue(venueName, location, totalSeats)
        }
    }
}
