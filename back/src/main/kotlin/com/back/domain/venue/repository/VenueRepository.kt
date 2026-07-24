package com.back.domain.venue.repository

import com.back.domain.venue.entity.Venue
import org.springframework.data.jpa.repository.JpaRepository

interface VenueRepository : JpaRepository<Venue, Long>
