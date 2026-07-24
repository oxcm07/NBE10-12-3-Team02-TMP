package com.back.domain.concert.repository

import com.back.domain.concert.entity.Concert
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ConcertRepository : JpaRepository<Concert, Long> {

    @Query("SELECT c FROM Concert c WHERE (:keyword IS NULL OR c.concertName LIKE %:keyword%)")
    fun findByKeyword(@Param("keyword") keyword: String?): List<Concert>
}
