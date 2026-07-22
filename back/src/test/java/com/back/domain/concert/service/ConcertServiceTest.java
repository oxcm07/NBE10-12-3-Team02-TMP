package com.back.domain.concert.service;

import com.back.domain.concert.entity.Concert;
import com.back.domain.concert.repository.ConcertDeatilRepository;
import com.back.domain.concert.repository.ConcertRepository;
import com.back.domain.schedule.entity.Schedule;
import com.back.domain.schedule.entity.ScheduleSeat;
import com.back.domain.schedule.entity.SeatStatus;
import com.back.domain.schedule.repository.ScheduleRepository;
import com.back.domain.schedule.repository.ScheduleSeatRepository;
import com.back.domain.venue.entity.Venue;
import com.back.domain.venue.repository.VenueRepository;
import com.back.global.RedisTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(RedisTestConfig.class)
class ConcertServiceTest {
    @Autowired
    private SeatOccupyManager seatOccupyManager;
    @Autowired
    private ConcertRepository concertRepository;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private ScheduleRepository scheduleRepository;
    @Autowired
    private ScheduleSeatRepository scheduleSeatRepository;
    @Autowired
    private ConcertDeatilRepository concertDeatilRepository;
    @Autowired
    private RedissonClient redissonClient;

    private Concert concert;
    private Schedule schedule;
    private ScheduleSeat seat;

    @BeforeEach
    void setUp() {
        scheduleSeatRepository.deleteAll();
        scheduleRepository.deleteAll();
        concertDeatilRepository.deleteAll();
        concertRepository.deleteAll();
        venueRepository.deleteAll();

        concert = concertRepository.save(Concert.create("아이유 콘서트", "설명", LocalDateTime.now(), LocalDateTime.now().plusDays(1), "poster.jpg"));
        Venue venue = venueRepository.save(Venue.create("올림픽체조경기장", "서울", 15000L));
        schedule = scheduleRepository.save(Schedule.create(concert, venue, LocalDateTime.now().plusHours(12), 1));

        for (int i = 1; i <= 100; i++) {
            ScheduleSeat createdSeat = scheduleSeatRepository.save(ScheduleSeat.create(schedule, "VIP", "A-" + i, 150000, SeatStatus.AVAILABLE));
            if (i == 1) {
                this.seat = createdSeat;
            }
        }

        // 이전 테스트의 Redis 선점 키 정리
        String pattern = "seat:occupy:" + concert.getConcertId() + ":" + schedule.getScheduleId() + ":*";
        redissonClient.getKeys().deleteByPattern(pattern);
    }

    @Test
    @DisplayName("실시간 좌석 선점 동시성 테스트")
    void seatOccupy() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger connectionTimeoutCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                final long userId = i + 1;
                executorService.execute(() -> {
                    try {
                        startLatch.await();
                        seatOccupyManager.seatOccupy(
                                concert.getConcertId(),
                                schedule.getScheduleId(),
                                seat.getSeatNumber(),
                                userId
                        );
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        String errName = e.getClass().getSimpleName();
                        String errMsg = e.getMessage() != null ? e.getMessage() : "";
                        Throwable cause = e.getCause();
                        String causeName = cause != null ? cause.getClass().getSimpleName() : "";
                        String causeMsg = cause != null && cause.getMessage() != null ? cause.getMessage() : "";

                        boolean isConnectionTimeout = errName.contains("Connection") ||
                                                     errName.contains("Timeout") ||
                                                     errName.contains("CannotCreateTransaction") ||
                                                     causeName.contains("Connection") ||
                                                     causeName.contains("Timeout") ||
                                                     errMsg.contains("HikariPool") ||
                                                     causeMsg.contains("HikariPool");

                        if (isConnectionTimeout) {
                            connectionTimeoutCount.incrementAndGet();
                        }

                        System.err.println(" 예외 발생 원인: " + errName + " - " + errMsg);
                        if (cause != null) {
                            System.err.println("   └─ 상세 원인: " + causeName + " - " + causeMsg);
                        }
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
        }

        long endTime = System.currentTimeMillis();
        System.out.println(">>> [성능 리포트] 총 소요 시간: " + (endTime - startTime) + " ms");
        System.out.println(">>> [성능 리포트] 커넥션 고갈 예외 수: " + connectionTimeoutCount.get() + " / " + threadCount);

        assertThat(connectionTimeoutCount.get())
                .as("커넥션 고갈 예외가 0개여야 합니다.")
                .isEqualTo(0);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);

        ScheduleSeat updatedSeat = scheduleSeatRepository.findById(seat.getConcertSeatPriceId()).orElseThrow();
        assertThat(updatedSeat.getSeatStatus()).isEqualTo(SeatStatus.HOLD);
    }

    @Test
    @DisplayName("조회 총 소요 시간 측정 테스트")
    void pipeliningBenchmark() {
        int requestCount = 100;

        long startTime = System.currentTimeMillis();
        Long userId = 1L;
        for (int i = 0; i < requestCount; i++) {
            seatOccupyManager.getSeatSelection(concert.getConcertId(), schedule.getScheduleId(), userId);
        }
        long endTime = System.currentTimeMillis();

        System.out.println(">>> 조회 총 소요 시간: " + (endTime - startTime) + " ms");
    }
}
