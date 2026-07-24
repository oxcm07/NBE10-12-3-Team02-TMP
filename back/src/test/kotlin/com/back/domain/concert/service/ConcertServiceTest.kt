package com.back.domain.concert.service

import com.back.domain.concert.entity.Concert
import com.back.domain.concert.repository.ConcertDeatilRepository
import com.back.domain.concert.repository.ConcertRepository
import com.back.domain.schedule.entity.Schedule
import com.back.domain.schedule.entity.ScheduleSeat
import com.back.domain.schedule.entity.SeatStatus
import com.back.domain.schedule.repository.ScheduleRepository
import com.back.domain.schedule.repository.ScheduleSeatRepository
import com.back.domain.venue.entity.Venue
import com.back.domain.venue.repository.VenueRepository
import com.back.global.RedisTestConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@ActiveProfiles("test")
@SpringBootTest
@Import(RedisTestConfig::class)
class ConcertServiceTest {
    @Autowired
    private lateinit var seatOccupyManager: SeatOccupyManager

    @Autowired
    private lateinit var concertRepository: ConcertRepository

    @Autowired
    private lateinit var venueRepository: VenueRepository

    @Autowired
    private lateinit var scheduleRepository: ScheduleRepository

    @Autowired
    private lateinit var scheduleSeatRepository: ScheduleSeatRepository

    @Autowired
    private lateinit var concertDeatilRepository: ConcertDeatilRepository

    @Autowired
    private lateinit var redissonClient: RedissonClient

    private lateinit var concert: Concert
    private lateinit var schedule: Schedule
    private lateinit var seat: ScheduleSeat

    @BeforeEach
    fun setUp() {
        scheduleSeatRepository.deleteAll()
        scheduleRepository.deleteAll()
        concertDeatilRepository.deleteAll()
        concertRepository.deleteAll()
        venueRepository.deleteAll()

        concert = concertRepository.save(
            Concert.create("아이유 콘서트", "설명", LocalDateTime.now(), LocalDateTime.now().plusDays(1), "poster.jpg")
        )
        val venue = venueRepository.save(Venue.create("올림픽체조경기장", "서울", 15000L))
        schedule = scheduleRepository.save(Schedule.create(concert, venue, LocalDateTime.now().plusHours(12), 1))

        for (i in 1..100) {
            val createdSeat = scheduleSeatRepository.save(
                ScheduleSeat.create(schedule, "VIP", "A-$i", 150000, SeatStatus.AVAILABLE)
            )
            if (i == 1) {
                this.seat = createdSeat
            }
        }

        val pattern = "seat:occupy:${concert.concertId}:${schedule.scheduleId}:*"
        redissonClient.keys.deleteByPattern(pattern)
    }

    @Test
    @DisplayName("실시간 좌석 선점 동시성 테스트")
    fun seatOccupy() {
        val threadCount = 10
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)
        val connectionTimeoutCount = AtomicInteger(0)

        val startTime = System.currentTimeMillis()

        val executorService = Executors.newFixedThreadPool(threadCount)
        try {
            for (i in 0 until threadCount) {
                val userId = (i + 1).toLong()
                executorService.execute {
                    try {
                        startLatch.await()
                        val concertId = concert.concertId ?: return@execute
                        val scheduleId = schedule.scheduleId ?: return@execute
                        seatOccupyManager.seatOccupy(
                            concertId,
                            scheduleId,
                            seat.seatNumber,
                            userId
                        )
                        successCount.incrementAndGet()
                    } catch (e: Exception) {
                        val errName = e.javaClass.simpleName
                        val errMsg = e.message ?: ""
                        val cause = e.cause
                        val causeName = cause?.javaClass?.simpleName ?: ""
                        val causeMsg = cause?.message ?: ""

                        val isConnectionTimeout = errName.contains("Connection") ||
                            errName.contains("Timeout") ||
                            errName.contains("CannotCreateTransaction") ||
                            causeName.contains("Connection") ||
                            causeName.contains("Timeout") ||
                            errMsg.contains("HikariPool") ||
                            causeMsg.contains("HikariPool")

                        if (isConnectionTimeout) {
                            connectionTimeoutCount.incrementAndGet()
                        }

                        System.err.println(" 예외 발생 원인: $errName - $errMsg")
                        if (cause != null) {
                            System.err.println("   └─ 상세 원인: $causeName - $causeMsg")
                        }
                        failCount.incrementAndGet()
                    } finally {
                        doneLatch.countDown()
                    }
                }
            }
            startLatch.countDown()
            doneLatch.await(10, TimeUnit.SECONDS)
        } finally {
            executorService.shutdownNow()
        }

        val endTime = System.currentTimeMillis()
        println(">>> [성능 리포트] 총 소요 시간: ${endTime - startTime} ms")
        println(">>> [성능 리포트] 커넥션 고갈 예외 수: ${connectionTimeoutCount.get()} / $threadCount")

        assertThat(connectionTimeoutCount.get())
            .`as`("커넥션 고갈 예외가 0개여야 합니다.")
            .isEqualTo(0)

        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failCount.get()).isEqualTo(threadCount - 1)

        val updatedSeat = scheduleSeatRepository.findById(seat.concertSeatPriceId!!).orElseThrow()
        assertThat(updatedSeat.seatStatus).isEqualTo(SeatStatus.HOLD)
    }

    @Test
    @DisplayName("조회 총 소요 시간 측정 테스트")
    fun pipeliningBenchmark() {
        val requestCount = 100

        val startTime = System.currentTimeMillis()
        val userId = 1L
        val concertId = concert.concertId!!
        val scheduleId = schedule.scheduleId!!
        for (i in 0 until requestCount) {
            seatOccupyManager.getSeatSelection(concertId, scheduleId, userId)
        }
        val endTime = System.currentTimeMillis()

        println(">>> 조회 총 소요 시간: ${endTime - startTime} ms")
    }
}
