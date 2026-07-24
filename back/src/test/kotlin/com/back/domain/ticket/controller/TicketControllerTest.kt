package com.back.domain.ticket.controller

import com.back.domain.concert.entity.Concert
import com.back.domain.concert.repository.ConcertRepository
import com.back.domain.schedule.entity.Schedule
import com.back.domain.schedule.entity.ScheduleSeat
import com.back.domain.schedule.entity.SeatStatus.*
import com.back.domain.schedule.repository.ScheduleRepository
import com.back.domain.schedule.repository.ScheduleSeatRepository
import com.back.domain.ticket.entity.Ticket
import com.back.domain.ticket.repository.TicketRepository
import com.back.domain.user.entity.LoginType
import com.back.domain.user.entity.User
import com.back.domain.user.repository.UserRepository
import com.back.domain.venue.entity.Venue
import com.back.domain.venue.repository.VenueRepository
import com.back.global.security.SecurityUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.redisson.api.RBlockingQueue
import org.redisson.api.RBucket
import org.redisson.api.RDelayedQueue
import org.redisson.api.RMap
import org.redisson.api.RScoredSortedSet
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TicketControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val concertRepository: ConcertRepository,
    private val venueRepository: VenueRepository,
    private val scheduleRepository: ScheduleRepository,
    private val scheduleSeatRepository: ScheduleSeatRepository,
    private val ticketRepository: TicketRepository
) {
    private lateinit var userEntity: User
    private lateinit var securityUser: SecurityUser
    private lateinit var concert: Concert
    private lateinit var schedule: Schedule
    private lateinit var seat: ScheduleSeat

    @MockitoBean
    private lateinit var redissonClient: RedissonClient

    @BeforeEach
    @Suppress("UNCHECKED_CAST")
    fun setUp() {
        userEntity = saveUser()
        securityUser = SecurityUser(userEntity.userId!!, userEntity.name)

        val activeSet = mock(RScoredSortedSet::class.java) as RScoredSortedSet<String>
        doReturn(activeSet).`when`(redissonClient).getScoredSortedSet<String>(anyString())
        `when`(activeSet.getScore(anyString())).thenReturn((System.currentTimeMillis() + 600000).toDouble())

        val tokenBucket = mock(RBucket::class.java) as RBucket<String>
        doReturn(tokenBucket).`when`(redissonClient).getBucket<String>(anyString())
        `when`(tokenBucket.get()).thenReturn("test-queue-token")

        val hashMap = mock(RMap::class.java) as RMap<String, String>
        doReturn(hashMap).`when`(redissonClient).getMap<String, String>(anyString())
        `when`(hashMap["userId"]).thenReturn(userEntity.userId.toString())
        `when`(hashMap["occupyToken"]).thenReturn("test-token")

        val blockingQueue = mock(RBlockingQueue::class.java) as RBlockingQueue<String>
        val delayedQueue = mock(RDelayedQueue::class.java) as RDelayedQueue<String>
        doReturn(blockingQueue).`when`(redissonClient).getBlockingQueue<String>(anyString())
        doReturn(delayedQueue).`when`(redissonClient).getDelayedQueue<String>(any())

        concert = concertRepository.save(
            Concert.create("싸이 콘서트", "설명", LocalDateTime.now(), LocalDateTime.now().plusDays(1), "poster.jpg")
        )
        val venue = venueRepository.save(Venue.create("공연장", "서울", 15000L))
        schedule = scheduleRepository.save(Schedule.create(concert, venue, LocalDateTime.now().plusHours(12), 1))

        seat = scheduleSeatRepository.save(ScheduleSeat.create(schedule, "VIP", "A-1", 150000, HOLD))
        scheduleSeatRepository.save(ScheduleSeat.create(schedule, "VIP", "A-2", 150000, HOLD))
        scheduleSeatRepository.save(ScheduleSeat.create(schedule, "VIP", "A-3", 150000, HOLD))
        scheduleSeatRepository.save(ScheduleSeat.create(schedule, "VIP", "A-4", 150000, HOLD))
    }

    @Test
    @DisplayName("티켓 2매 생성 성공")
    @Suppress("UNCHECKED_CAST")
    fun createTicket() {
        val hashMap = mock(RMap::class.java) as RMap<String, String>
        doReturn(hashMap).`when`(redissonClient).getMap<String, String>(anyString())
        `when`(redissonClient.getMap<String, String>(anyString())).thenAnswer { invocation ->
            val key = invocation.getArgument(0, String::class.java)
            val map = mock(RMap::class.java) as RMap<String, String>
            when {
                key.endsWith("A-1") -> {
                    `when`(map["userId"]).thenReturn(userEntity.userId.toString())
                    `when`(map["occupyToken"]).thenReturn("token-1")
                }
                key.endsWith("A-2") -> {
                    `when`(map["userId"]).thenReturn(userEntity.userId.toString())
                    `when`(map["occupyToken"]).thenReturn("token-2")
                }
                else -> {
                    `when`(map["userId"]).thenReturn(userEntity.userId.toString())
                    `when`(map["occupyToken"]).thenReturn("test-token")
                }
            }
            map
        }

        val requestBody = """
            {
              "concertId": ${concert.concertId},
              "seatHolds": [
                {
                  "seatNumber": "A-1",
                  "occupyToken": "token-1"
                },
                {
                  "seatNumber": "A-2",
                  "occupyToken": "token-2"
                }
              ]
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/tickets/reserve/schedule/{scheduleId}", schedule.scheduleId)
                .header("X-Queue-Token", "test-queue-token")
                .with(user(securityUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resultCode").value("201-1"))
            .andExpect(jsonPath("$.msg").value("결제 및 티켓 생성 성공"))
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].ticketNumber").isString)
            .andExpect(jsonPath("$.data[0].urlPoster").value("poster.jpg"))
            .andExpect(jsonPath("$.data[0].concertName").value("싸이 콘서트"))
            .andExpect(jsonPath("$.data[0].seatNumber").value("A-1"))
            .andExpect(jsonPath("$.data[1].ticketNumber").isString)
            .andExpect(jsonPath("$.data[1].urlPoster").value("poster.jpg"))
            .andExpect(jsonPath("$.data[1].concertName").value("싸이 콘서트"))
            .andExpect(jsonPath("$.data[1].seatNumber").value("A-2"))

        assertThat(seat.seatStatus).isEqualTo(SOLD_OUT)
        assertThat(ticketRepository.count()).isEqualTo(2)
    }

    @Test
    @DisplayName("티켓 4매 생성 실패")
    fun createFourTickets() {
        val requestBody = """
            {
              "concertId": ${concert.concertId},
              "seatHolds": [
                { "seatNumber": "A-1", "occupyToken": "token-1" },
                { "seatNumber": "A-2", "occupyToken": "token-2" },
                { "seatNumber": "A-3", "occupyToken": "token-3" },
                { "seatNumber": "A-4", "occupyToken": "token-4" }
              ]
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/tickets/reserve/schedule/{scheduleId}", schedule.scheduleId)
                .header("X-Queue-Token", "test-queue-token")
                .with(user(securityUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andDo(print())
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-2"))
            .andExpect(jsonPath("$.msg").value("회차당 최대 3매까지 예매 가능합니다."))
    }

    @Test
    @DisplayName("티켓 취소 성공")
    fun cancelTicket() {
        seat.occupyHold()
        seat.sell()
        val ticket = ticketRepository.save(Ticket.create(userEntity, schedule, seat, "ticket-number", seat.seatPrice))

        mockMvc.perform(
            patch("/api/v1/tickets/cancel/{ticketId}", ticket.ticketId)
                .with(user(securityUser))
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("티켓 취소 성공"))

        assertThat(ticket.isValid).isFalse
        assertThat(seat.seatStatus).isEqualTo(AVAILABLE)
    }

    private fun saveUser(): User {
        return userRepository.save(User.create("user1", "user1@test.com", "0000", "테스트 유저", LoginType.NORMAL))
    }
}
