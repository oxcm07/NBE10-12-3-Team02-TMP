package com.back.domain.schedule.controller;

import com.back.domain.concert.entity.Concert;
import com.back.domain.concert.repository.ConcertRepository;
import com.back.domain.schedule.entity.Schedule;
import com.back.domain.schedule.entity.ScheduleSeat;
import com.back.domain.schedule.repository.ScheduleRepository;
import com.back.domain.schedule.repository.ScheduleSeatRepository;
import com.back.domain.venue.entity.Venue;
import com.back.domain.venue.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.back.domain.schedule.entity.SeatStatus.AVAILABLE;
import static com.back.domain.schedule.entity.SeatStatus.HOLD;
import static com.back.domain.schedule.entity.SeatStatus.SOLD_OUT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@org.springframework.context.annotation.Import(com.back.global.RedisTestConfig.class)
class ScheduleControllerTest {
    private final MockMvc mockMvc;
    private final ConcertRepository concertRepository;
    private final VenueRepository venueRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleSeatRepository scheduleSeatRepository;

    private Concert concert;
    private Schedule schedule;

    @Autowired
    ScheduleControllerTest(
            MockMvc mockMvc,
            ConcertRepository concertRepository,
            VenueRepository venueRepository,
            ScheduleRepository scheduleRepository,
            ScheduleSeatRepository scheduleSeatRepository
    ) {
        this.mockMvc = mockMvc;
        this.concertRepository = concertRepository;
        this.venueRepository = venueRepository;
        this.scheduleRepository = scheduleRepository;
        this.scheduleSeatRepository = scheduleSeatRepository;
    }

    @BeforeEach
    void setUp() {
        concert = concertRepository.save(Concert.create(
                "싸이 콘서트",
                "설명",
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(1),
                "poster.jpg"
        ));
        Venue venue = venueRepository.save(Venue.create("공연장", "서울", 15000L));
        schedule = scheduleRepository.save(Schedule.create(concert, venue, LocalDateTime.of(2026, 7, 1, 19, 0), 1));

        scheduleSeatRepository.save(ScheduleSeat.create(schedule, "VIP", "A-1", 150000, AVAILABLE));
        scheduleSeatRepository.save(ScheduleSeat.create(schedule, "VIP", "A-2", 150000, AVAILABLE));
        scheduleSeatRepository.save(ScheduleSeat.create(schedule, "VIP", "A-3", 150000, HOLD));
        scheduleSeatRepository.save(ScheduleSeat.create(schedule, "VIP", "A-4", 150000, SOLD_OUT));
    }

    @Test
    @DisplayName("특정 회차 좌석 실시간 현황 조회 성공")
    void showSchedule() throws Exception {
        mockMvc.perform(get("/api/v1/schedules/{scheduleId}/seats/status", schedule.getScheduleId())
                        .param("concertId", String.valueOf(concert.getConcertId())))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("특정 회차 좌석 실시간 조회 성공"))
                .andExpect(jsonPath("$.data.concertId").value(concert.getConcertId()))
                .andExpect(jsonPath("$.data.scheduleId").value(schedule.getScheduleId()))
                .andExpect(jsonPath("$.data.round").value(1))
                .andExpect(jsonPath("$.data.scheduleDate").value("2026-07-01T19:00:00"))
                .andExpect(jsonPath("$.data.remainingSeats").value(2));
    }
}
