package com.back.domain.ticket.service;

import com.back.domain.concert.listener.SeatHoldExpiredHandler;
import com.back.domain.concert.listener.SeatOccupiedEventListener;
import com.back.domain.concert.service.SeatOccupyManager;
import com.back.domain.concert.sse.SeatStatusSseEmitterRegistry;
import com.back.domain.schedule.entity.Schedule;
import com.back.domain.schedule.entity.ScheduleSeat;
import com.back.domain.schedule.entity.SeatStatus;
import com.back.domain.schedule.repository.ScheduleRepository;
import com.back.domain.schedule.repository.ScheduleSeatRepository;
import com.back.domain.ticket.dto.PaymentTicketRequest;
import com.back.domain.ticket.dto.PaymentTicketResponse;
import com.back.domain.ticket.dto.SeatHoldInfo;
import com.back.domain.ticket.entity.Ticket;
import com.back.domain.ticket.event.PaymentCompletedEvent;
import com.back.domain.ticket.event.TicketCancelledEvent;
import com.back.domain.ticket.repository.TicketRepository;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.exception.ErrorCode;
import com.back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * 티켓 예매 및 결제 서비스.
 *
 * <h2>결제 완료 시 처리 흐름</h2>
 * <ol>
 *   <li>비관적 락({@code PESSIMISTIC_WRITE})으로 좌석 레코드 잠금</li>
 *   <li>DB HOLD 상태 및 Redis 선점 토큰 검증 (이중 보안)</li>
 *   <li>좌석 상태를 {@code SOLD_OUT}으로 업데이트</li>
 *   <li>Redis 선점 Hash / ZSET 인덱스 삭제</li>
 *   <li>Redisson Delayed Queue에서 해당 좌석의 만료 메시지 제거 (복구 방지)</li>
 *   <li>티켓 엔티티 저장 후 SSE 브로드캐스트 이벤트 발행</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleSeatRepository scheduleSeatRepository;
    private final RedissonClient redissonClient;
    private final ApplicationEventPublisher eventPublisher;
    private final SeatOccupyManager seatOccupyManager;
    private final SeatStatusSseEmitterRegistry sseEmitterRegistry;

    @Transactional
    public List<PaymentTicketResponse> createTicket(Long userId, Long scheduleId, PaymentTicketRequest request) {
        User user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.USER_NOT_FOUND));

        Schedule schedule = scheduleRepository
                .findByScheduleIdAndConcert_ConcertId(scheduleId, request.concertId())
                .orElseThrow(() -> new ServiceException(ErrorCode.INVALID_CONCERT_SCHEDULE));

        long alreadyPurchasedCount = ticketRepository
                .countByUser_UserIdAndSchedule_ScheduleIdAndIsValidTrue(userId, scheduleId);
        if (alreadyPurchasedCount + request.seatHolds().size() > 3) {
            throw new ServiceException(ErrorCode.EXCEED_TICKET_LIMIT);
        }

        // 데드락 방지를 위한 좌석 번호 정렬
        List<SeatHoldInfo> sortedSeatHolds = request.seatHolds().stream()
                .sorted(Comparator.comparing(SeatHoldInfo::seatNumber))
                .toList();

        List<ScheduleSeat> scheduleSeats = new ArrayList<>();
        for (SeatHoldInfo holdInfo : sortedSeatHolds) {
            ScheduleSeat scheduleSeat = scheduleSeatRepository
                    .findWithLockByScheduleIdAndSeatNumber(scheduleId, holdInfo.seatNumber())
                    .orElseThrow(() -> new ServiceException(ErrorCode.SEAT_NOT_FOUND));

            // DB HOLD 상태 검증 (DB가 SSOT이므로 HOLD가 아니면 비정상)
            if (scheduleSeat.getSeatStatus() == SeatStatus.SOLD_OUT) {
                throw new ServiceException(ErrorCode.SEAT_ALREADY_SOLD);
            }
            if (scheduleSeat.getSeatStatus() != SeatStatus.HOLD) {
                // 선점 만료 또는 비정상 요청
                throw new ServiceException(ErrorCode.SEAT_HOLD_EXPIRED);
            }

            scheduleSeats.add(scheduleSeat);
        }

        // Redis 토큰 검증 (선점 소유권 확인)
        validateSeatHold(userId, request.concertId(), scheduleId, sortedSeatHolds);

        // 좌석 상태 SOLD_OUT으로 확정
        scheduleSeats.forEach(seat -> seat.updateSeatStatus(SeatStatus.SOLD_OUT));

        // Redis 선점 데이터 삭제
        for (SeatHoldInfo holdInfo : sortedSeatHolds) {
            String redisKey = SeatOccupyManager.generateSeatOccupyKey(
                    request.concertId(), scheduleId, holdInfo.seatNumber());
            String indexKey = SeatOccupyManager.generateSeatOccupyIndexKey(
                    request.concertId(), scheduleId);
            seatOccupyManager.cleanupRedis(redisKey, indexKey, holdInfo.seatNumber());

            // Delayed Queue에서 만료 메시지 제거 (결제 완료 후 불필요한 복구 방지)
            cancelDelayedQueueMessage(request.concertId(), scheduleId, holdInfo.seatNumber());

            // SSE: 결제 완료된 좌석을 SOLD_OUT으로 브로드캐스트
            sseEmitterRegistry.broadcast(scheduleId, holdInfo.seatNumber(), SeatStatus.SOLD_OUT.name());
        }

        List<Ticket> tickets = scheduleSeats.stream()
                .map(seat -> Ticket.create(user, schedule, seat, createTicketNumber(), seat.getSeatPrice()))
                .toList();
        ticketRepository.saveAll(tickets);

        eventPublisher.publishEvent(new PaymentCompletedEvent(
                request.concertId(), scheduleId, userId));

        return IntStream.range(0, tickets.size())
                .mapToObj(i -> PaymentTicketResponse.from(scheduleSeats.get(i), schedule, tickets.get(i)))
                .toList();
    }

    @Transactional
    public void cancelTicket(Long userId, Long ticketId) {
        Ticket ticket = ticketRepository.findByTicketIdAndUser_UserId(ticketId, userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.TICKET_NOT_FOUND_FOR_USER));

        if (!ticket.isValid()) {
            throw new ServiceException(ErrorCode.TICKET_ALREADY_CANCELLED);
        }

        ticket.updateIsValid(false);
        ticket.getScheduleSeat().updateSeatStatus(SeatStatus.AVAILABLE);

        // Redis 선점 데이터 정리
        Long concertId = ticket.getSchedule().getConcert().getConcertId();
        Long scheduleId = ticket.getSchedule().getScheduleId();
        String seatNumber = ticket.getScheduleSeat().getSeatNumber();

        String redisKey = SeatOccupyManager.generateSeatOccupyKey(concertId, scheduleId, seatNumber);
        String indexKey = SeatOccupyManager.generateSeatOccupyIndexKey(concertId, scheduleId);
        seatOccupyManager.cleanupRedis(redisKey, indexKey, seatNumber);

        // Delayed Queue 메시지도 정리
        cancelDelayedQueueMessage(concertId, scheduleId, seatNumber);

        // SSE: 취소된 좌석을 AVAILABLE로 브로드캐스트
        sseEmitterRegistry.broadcast(scheduleId, seatNumber, SeatStatus.AVAILABLE.name());

        eventPublisher.publishEvent(new TicketCancelledEvent(concertId, scheduleId, userId));
    }

    public String createTicketNumber() {
        return UUID.randomUUID().toString();
    }

    private void validateSeatHold(Long userId, Long concertId, Long scheduleId, List<SeatHoldInfo> seatHolds) {
        for (SeatHoldInfo hold : seatHolds) {
            String redisKey = SeatOccupyManager.generateSeatOccupyKey(concertId, scheduleId, hold.seatNumber());
            RMap<String, String> hashMap = redissonClient.getMap(redisKey);

            String holdUserId = hashMap.get("userId");
            String holdOccupyToken = hashMap.get("occupyToken");

            if (holdUserId == null || holdOccupyToken == null) {
                throw new ServiceException(ErrorCode.SEAT_HOLD_EXPIRED);
            }
            if (!userId.toString().equals(holdUserId)) {
                throw new ServiceException(ErrorCode.SEAT_HELD_BY_OTHER_USER);
            }
            if (!hold.occupyToken().equals(holdOccupyToken)) {
                throw new ServiceException(ErrorCode.INVALID_OCCUPY_TOKEN);
            }
        }
    }

    /**
     * Redisson Delayed Queue에서 특정 좌석의 만료 메시지를 제거합니다.
     * (결제 완료 또는 티켓 취소 후 불필요한 AVAILABLE 복구 방지)
     */
    private void cancelDelayedQueueMessage(Long concertId, Long scheduleId, String seatNumber) {
        try {
            String message = SeatOccupiedEventListener.buildMessage(concertId, scheduleId, seatNumber);
            RBlockingQueue<String> blockingQueue = redissonClient.getBlockingQueue(
                    SeatHoldExpiredHandler.DELAYED_QUEUE_KEY);
            RDelayedQueue<String> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
            delayedQueue.remove(message);
        } catch (Exception e) {
            // 큐 정리 실패는 치명적이지 않음 (메시지가 이미 소비되었거나 없는 경우)
        }
    }
}