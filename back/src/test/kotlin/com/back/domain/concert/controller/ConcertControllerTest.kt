package com.back.domain.concert.controller

import com.back.domain.concert.entity.Concert
import com.back.domain.concert.repository.ConcertRepository
import com.back.domain.schedule.entity.Schedule
import com.back.domain.schedule.entity.ScheduleSeat
import com.back.domain.schedule.entity.SeatStatus.AVAILABLE
import com.back.domain.schedule.entity.SeatStatus.HOLD
import com.back.domain.schedule.repository.ScheduleRepository
import com.back.domain.schedule.repository.ScheduleSeatRepository
import com.back.domain.venue.entity.Venue
import com.back.domain.venue.repository.VenueRepository
import com.back.global.security.SecurityUser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.redisson.api.RBlockingQueue
import org.redisson.api.RBucket
import org.redisson.api.RDelayedQueue
import org.redisson.api.RMap
import org.redisson.api.RScoredSortedSet
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.codec.Codec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConcertControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val concertRepository: ConcertRepository,
    private val venueRepository: VenueRepository,
    private val scheduleRepository: ScheduleRepository,
    private val scheduleSeatRepository: ScheduleSeatRepository
) {
    private lateinit var concert: Concert
    private lateinit var schedule: Schedule

    @MockitoBean
    private lateinit var redissonClient: RedissonClient

    @BeforeEach
    @Suppress("UNCHECKED_CAST")
    fun setUp() {
        val activeSet = mock(RScoredSortedSet::class.java) as RScoredSortedSet<String>
        doReturn(activeSet).`when`(redissonClient).getScoredSortedSet<String>(anyString())
        `when`(activeSet.getScore(anyString())).thenReturn((System.currentTimeMillis() + 600000).toDouble())

        val tokenBucket = mock(RBucket::class.java) as RBucket<String>
        doReturn(tokenBucket).`when`(redissonClient).getBucket<String>(anyString())
        `when`(tokenBucket.get()).thenReturn("test-queue-token")

        concert = Concert.create("아이유 콘서트", "설명", LocalDateTime.now(), LocalDateTime.now().plusDays(1), "poster.jpg")
        concertRepository.save(concert)

        val venue = Venue.create("올림픽체조경기장", "서울", 15000L)
        venueRepository.save(venue)

        schedule = Schedule.create(concert, venue, LocalDateTime.now().plusHours(12), 1)
        scheduleRepository.save(schedule)
    }

    @Test
    @DisplayName("좌석 선택 페이지 조회 성공")
    fun t1() {
        val seat1 = ScheduleSeat.create(schedule, "VIP", "A-1", 150000, AVAILABLE)
        scheduleSeatRepository.save(seat1)

        val seat2 = ScheduleSeat.create(schedule, "A", "B-2", 70000, AVAILABLE)
        scheduleSeatRepository.save(seat2)

        mockMvc.perform(
            get("/api/v1/concerts/{concertId}/schedules/{scheduleId}/seats", concert.concertId, schedule.scheduleId)
                .header("X-Queue-Token", "test-queue-token")
                .with(user(SecurityUser(1L, "테스트유저")))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("좌석 선택 페이지 조회 성공"))
            .andExpect(jsonPath("$.data.concertId").value(concert.concertId))
            .andExpect(jsonPath("$.data.scheduleId").value(schedule.scheduleId))
            .andExpect(jsonPath("$.data.prices.VIP").value(150000))
            .andExpect(jsonPath("$.data.prices.A").value(70000))
            .andExpect(jsonPath("$.data.seats[0].seatNumber").value("A-1"))
            .andExpect(jsonPath("$.data.seats[0].seatStatus").value("AVAILABLE"))
            .andExpect(jsonPath("$.data.seats[0].gradeName").value("VIP"))
            .andExpect(jsonPath("$.data.seats[1].seatNumber").value("B-2"))
            .andExpect(jsonPath("$.data.seats[1].seatStatus").value("AVAILABLE"))
            .andExpect(jsonPath("$.data.seats[1].gradeName").value("A"))
    }

    @Test
    @DisplayName("콘서트 목록 조회 성공")
    fun t2() {
        mockMvc.perform(
            get("/api/v1/concerts")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("콘서트 목록 조회 성공"))
            .andExpect(jsonPath("$.data[0].concertName").value("아이유 콘서트"))
            .andExpect(jsonPath("$.data[0].venueName").value("올림픽체조경기장"))
            .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"))
    }

    @Test
    @DisplayName("콘서트 상세 조회 성공")
    fun t3() {
        val seat = ScheduleSeat.create(schedule, "VIP", "A-1", 150000, AVAILABLE)
        scheduleSeatRepository.save(seat)

        mockMvc.perform(
            get("/api/v1/concerts/{concertId}", concert.concertId)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("콘서트 상세 정보 조회 성공"))
            .andExpect(jsonPath("$.data.concertId").value(concert.concertId))
            .andExpect(jsonPath("$.data.concertName").value("아이유 콘서트"))
            .andExpect(jsonPath("$.data.description").value("설명"))
            .andExpect(jsonPath("$.data.venueName").value("올림픽체조경기장"))
            .andExpect(jsonPath("$.data.location").value("서울"))
            .andExpect(jsonPath("$.data.prices.VIP").value(150000))
            .andExpect(jsonPath("$.data.bookable").value(true))
    }

    @Test
    @DisplayName("좌석 임시 선점 성공")
    @Suppress("UNCHECKED_CAST")
    fun t4() {
        val seat = ScheduleSeat.create(schedule, "VIP", "A-1", 150000, AVAILABLE)
        scheduleSeatRepository.save(seat)

        val rScript = mock(RScript::class.java)
        doReturn(rScript).`when`(redissonClient).getScript()
        doReturn(rScript).`when`(redissonClient).getScript(any(Codec::class.java))
        `when`(rScript.eval<Any>(any(), anyString(), any(), anyList<Any>(), any(), any(), any(), any(), any())).thenReturn(1L)

        val rMap = mock(RMap::class.java) as RMap<String, String>
        doReturn(rMap).`when`(redissonClient).getMap<String, String>(anyString())

        val blockingQueue = mock(RBlockingQueue::class.java) as RBlockingQueue<String>
        val delayedQueue = mock(RDelayedQueue::class.java) as RDelayedQueue<String>
        doReturn(blockingQueue).`when`(redissonClient).getBlockingQueue<String>(anyString())
        doReturn(delayedQueue).`when`(redissonClient).getDelayedQueue<String>(any())

        val requestBody = """
            {
              "seatNumber": "A-1"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/concerts/{concertId}/schedules/{scheduleId}/seats/occupy", concert.concertId, schedule.scheduleId)
                .header("X-Queue-Token", "test-queue-token")
                .with(user(SecurityUser(1L, "테스트유저")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("좌석 임시 선점에 성공했습니다."))
            .andExpect(jsonPath("$.data.occupyToken").isString)
            .andExpect(jsonPath("$.data.expireInSeconds").value(600))
            .andExpect(jsonPath("$.data.seatStatus").value("HOLD"))
    }

    @Test
    @DisplayName("좌석 임시 선점 취소 성공")
    @Suppress("UNCHECKED_CAST")
    fun t5() {
        val userId = 1L
        val seatNumber = "A-1"

        val seat = ScheduleSeat.create(schedule, "VIP", seatNumber, 150000, HOLD)
        scheduleSeatRepository.save(seat)

        val rMap = mock(RMap::class.java) as RMap<String, String>
        doReturn(rMap).`when`(redissonClient).getMap<String, String>(anyString())
        `when`(rMap["userId"]).thenReturn(userId.toString())

        val requestBody = """
            {
              "seatNumber": "A-1"
            }
        """.trimIndent()

        mockMvc.perform(
            delete("/api/v1/concerts/{concertId}/schedules/{scheduleId}/seats/occupy", concert.concertId, schedule.scheduleId)
                .with(user(SecurityUser(userId, "테스트유저")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("좌석 선점이 정상적으로 취소되었습니다."))
    }
}
