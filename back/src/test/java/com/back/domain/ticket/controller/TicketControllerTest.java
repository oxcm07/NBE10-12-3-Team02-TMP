package com.back.domain.ticket.controller;

import com.back.domain.concert.entity.Concert;
import com.back.domain.concert.repository.ConcertRepository;
import com.back.domain.schedule.entity.Schedule;
import com.back.domain.schedule.entity.ScheduleSeat;
import com.back.domain.schedule.repository.ScheduleRepository;
import com.back.domain.schedule.repository.ScheduleSeatRepository;
import com.back.domain.ticket.entity.Ticket;
import com.back.domain.ticket.repository.TicketRepository;
import com.back.domain.user.entity.LoginType;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.domain.venue.entity.Venue;
import com.back.domain.venue.repository.VenueRepository;
import com.back.global.security.SecurityUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RBucket;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.back.domain.schedule.entity.SeatStatus.AVAILABLE;
import static com.back.domain.schedule.entity.SeatStatus.HOLD;
import static com.back.domain.schedule.entity.SeatStatus.SOLD_OUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TicketControllerTest {
    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final ConcertRepository concertRepository;
    private final VenueRepository venueRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleSeatRepository scheduleSeatRepository;
    private final TicketRepository ticketRepository;

    private User user;
    private SecurityUser securityUser;
    private Concert concert;
    private Schedule schedule;
    private ScheduleSeat seat;

    @MockitoBean
    private RedissonClient redissonClient;

    @Autowired
    TicketControllerTest(
            MockMvc mockMvc,
            UserRepository userRepository,
            ConcertRepository concertRepository,
            VenueRepository venueRepository,
            ScheduleRepository scheduleRepository,
            ScheduleSeatRepository scheduleSeatRepository,
            TicketRepository ticketRepository
    ) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.concertRepository = concertRepository;
        this.venueRepository = venueRepository;
        this.scheduleRepository = scheduleRepository;
        this.scheduleSeatRepository = scheduleSeatRepository;
        this.ticketRepository = ticketRepository;
    }

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        user = saveUser();
        securityUser = new SecurityUser(user.getUserId(), user.getName());

        // QueueInterceptor: 진입열 ZSET score 반환 (유효한 세션)
        RScoredSortedSet<String> activeSet = mock(RScoredSortedSet.class);
        doReturn(activeSet).when(redissonClient).getScoredSortedSet(anyString());
        when(activeSet.getScore(anyString()))
                .thenReturn((double) (System.currentTimeMillis() + 600000));

        // QueueInterceptor: 토큰 조회
        RBucket<String> tokenBucket = mock(RBucket.class);
        doReturn(tokenBucket).when(redissonClient).getBucket(anyString());
        when(tokenBucket.get()).thenReturn("test-queue-token");

        // TicketService.validateSeatHold: RMap 기본값 (setUp에서 덮어씀)
        RMap<String, String> hashMap = mock(RMap.class);
        doReturn(hashMap).when(redissonClient).getMap(anyString());
        when(hashMap.get("userId")).thenReturn(user.getUserId().toString());
        when(hashMap.get("occupyToken")).thenReturn("test-token");

        // TicketService.cancelDelayedQueueMessage: Delayed Queue mock
        RBlockingQueue<String> blockingQueue = mock(RBlockingQueue.class);
        RDelayedQueue<String> delayedQueue = mock(RDelayedQueue.class);
        doReturn(blockingQueue).when(redissonClient).getBlockingQueue(anyString());
        doReturn(delayedQueue).when(redissonClient).getDelayedQueue(any());

        concert = concertRepository.save(Concert.create(
                "싸이 콘서트", "설명", LocalDateTime.now(), LocalDateTime.now().plusDays(1), "poster.jpg"));
        Venue venue = venueRepository.save(Venue.create("공연장", "서울", 15000L));
        schedule = scheduleRepository.save(Schedule.create(concert, venue, LocalDateTime.now().plusHours(12), 1));

        // HOLD 상태로 생성 (결제 검증이 HOLD 상태를 요구함)
        seat = scheduleSeatRepository.save(ScheduleSeat.create(schedule, "VIP", "A-1", 150000, HOLD));
        scheduleSeatRepository.save(ScheduleSeat.create(schedule, "VIP", "A-2", 150000, HOLD));
        scheduleSeatRepository.save(ScheduleSeat.create(schedule, "VIP", "A-3", 150000, HOLD));
        scheduleSeatRepository.save(ScheduleSeat.create(schedule, "VIP", "A-4", 150000, HOLD));
    }

    @Test
    @DisplayName("티켓 2매 생성 성공")
    @SuppressWarnings("unchecked")
    void createTicket() throws Exception {
        // A-1, A-2 에 대한 토큰 반환
        RMap<String, String> hashMap = mock(RMap.class);
        doReturn(hashMap).when(redissonClient).getMap(anyString());
        when(redissonClient.getMap(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            RMap<String, String> map = mock(RMap.class);
            if (key.endsWith("A-1")) {
                when(map.get("userId")).thenReturn(user.getUserId().toString());
                when(map.get("occupyToken")).thenReturn("token-1");
            } else if (key.endsWith("A-2")) {
                when(map.get("userId")).thenReturn(user.getUserId().toString());
                when(map.get("occupyToken")).thenReturn("token-2");
            } else {
                when(map.get("userId")).thenReturn(user.getUserId().toString());
                when(map.get("occupyToken")).thenReturn("test-token");
            }
            return map;
        });

        String requestBody = """
                {
                  "concertId": %d,
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
                """.formatted(concert.getConcertId());

        mockMvc.perform(post("/api/v1/tickets/reserve/schedule/{scheduleId}", schedule.getScheduleId())
                        .header("X-Queue-Token", "test-queue-token")
                        .with(user(securityUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.msg").value("결제 및 티켓 생성 성공"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].ticketNumber").isString())
                .andExpect(jsonPath("$.data[0].urlPoster").value("poster.jpg"))
                .andExpect(jsonPath("$.data[0].concertName").value("싸이 콘서트"))
                .andExpect(jsonPath("$.data[0].seatNumber").value("A-1"))
                .andExpect(jsonPath("$.data[1].ticketNumber").isString())
                .andExpect(jsonPath("$.data[1].urlPoster").value("poster.jpg"))
                .andExpect(jsonPath("$.data[1].concertName").value("싸이 콘서트"))
                .andExpect(jsonPath("$.data[1].seatNumber").value("A-2"));

        assertThat(seat.getSeatStatus()).isEqualTo(SOLD_OUT);
        assertThat(ticketRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("티켓 4매 생성 실패")
    void createFourTickets() throws Exception {
        String requestBody = """
                {
                  "concertId": %d,
                  "seatHolds": [
                    { "seatNumber": "A-1", "occupyToken": "token-1" },
                    { "seatNumber": "A-2", "occupyToken": "token-2" },
                    { "seatNumber": "A-3", "occupyToken": "token-3" },
                    { "seatNumber": "A-4", "occupyToken": "token-4" }
                  ]
                }
                """.formatted(concert.getConcertId());

        mockMvc.perform(post("/api/v1/tickets/reserve/schedule/{scheduleId}", schedule.getScheduleId())
                        .header("X-Queue-Token", "test-queue-token")
                        .with(user(securityUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-2"))
                .andExpect(jsonPath("$.msg").value("회차당 최대 3매까지 예매 가능합니다."));
    }

    @Test
    @DisplayName("티켓 취소 성공")
    void cancelTicket() throws Exception {
        seat.updateSeatStatus(SOLD_OUT);
        Ticket ticket = ticketRepository.save(Ticket.create(user, schedule, seat, "ticket-number", seat.getSeatPrice()));

        mockMvc.perform(patch("/api/v1/tickets/cancel/{ticketId}", ticket.getTicketId())
                        .with(user(securityUser)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("티켓 취소 성공"));

        assertThat(ticket.isValid()).isFalse();
        assertThat(seat.getSeatStatus()).isEqualTo(AVAILABLE);
    }

    private User saveUser() {
        return userRepository.save(
                User.create("user1", "user1@test.com", "0000", "테스트 유저", LoginType.NORMAL));
    }
}
