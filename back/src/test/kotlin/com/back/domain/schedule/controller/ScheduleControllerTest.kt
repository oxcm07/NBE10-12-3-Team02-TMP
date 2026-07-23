package com.back.domain.schedule.controller

import com.back.domain.concert.entity.Concert
import com.back.domain.concert.repository.ConcertRepository
import com.back.domain.schedule.entity.Schedule
import com.back.domain.schedule.entity.ScheduleSeat
import com.back.domain.schedule.entity.SeatStatus.*
import com.back.domain.schedule.repository.ScheduleRepository
import com.back.domain.schedule.repository.ScheduleSeatRepository
import com.back.domain.venue.entity.Venue
import com.back.domain.venue.repository.VenueRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ScheduleControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val concertRepository: ConcertRepository,
    private val venueRepository: VenueRepository,
    private val scheduleRepository: ScheduleRepository,
    private val scheduleSeatRepository: ScheduleSeatRepository
) {
    private lateinit var concert: Concert
    private lateinit var schedule: Schedule

    @BeforeEach
    fun setUp() {
        concert = concertRepository.save(Concert.create(
            "싸이 콘서트",
            "설명",
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(1),
            "poster.jpg"
        ))
        val venue = venueRepository.save(Venue.create("공연장", "서울", 15000L))
        schedule = scheduleRepository.save(Schedule.create(concert, venue, LocalDateTime.of(2026, 7, 1, 19, 0), 1))

        scheduleSeatRepository.save(ScheduleSeat.create(schedule, "VIP", "A-1", 150000, AVAILABLE))
        scheduleSeatRepository.save(ScheduleSeat.create(schedule, "VIP", "A-2", 150000, AVAILABLE))
        scheduleSeatRepository.save(ScheduleSeat.create(schedule, "VIP", "A-3", 150000, HOLD))
        scheduleSeatRepository.save(ScheduleSeat.create(schedule, "VIP", "A-4", 150000, SOLD_OUT))
    }

    @Test
    @DisplayName("특정 회차 좌석 실시간 현황 조회 성공")
    fun showSchedule() {
        mockMvc.perform(get("/api/v1/schedules/{scheduleId}/seats/status", schedule.scheduleId)
                .param("concertId", concert.concertId.toString()))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("특정 회차 좌석 실시간 조회 성공"))
            .andExpect(jsonPath("$.data.concertId").value(concert.concertId))
            .andExpect(jsonPath("$.data.scheduleId").value(schedule.scheduleId))
            .andExpect(jsonPath("$.data.round").value(1))
            .andExpect(jsonPath("$.data.scheduleDate").value("2026-07-01T19:00:00"))
            .andExpect(jsonPath("$.data.remainingSeats").value(2))
    }
}
