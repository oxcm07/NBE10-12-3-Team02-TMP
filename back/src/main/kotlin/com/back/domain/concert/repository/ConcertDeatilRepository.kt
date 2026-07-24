package com.back.domain.concert.repository

import com.back.domain.concert.entity.ConcertDetail
import org.springframework.data.jpa.repository.JpaRepository

interface ConcertDeatilRepository : JpaRepository<ConcertDetail, Long> {
    fun findByConcertConcertId(concertId: Long): List<ConcertDetail>
}
