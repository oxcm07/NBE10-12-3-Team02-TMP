package com.back.domain.concert.service;

import com.back.domain.concert.dto.SeatOccupyResponse;
import com.back.domain.concert.dto.SeatSelectionResponse;
import com.back.domain.concert.dto.SeatSelectionResponse.SeatDetailResponse;
import com.back.domain.concert.event.SeatOccupiedEvent;
import com.back.domain.schedule.entity.ScheduleSeat;
import com.back.domain.schedule.entity.SeatStatus;
import com.back.domain.schedule.repository.ScheduleSeatRepository;
import com.back.domain.ticket.repository.TicketRepository;
import com.back.global.exception.ErrorCode;
import com.back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Redis 기반 실시간 좌석 선점 관리 컴포넌트.
 *
 * <h2>좌석 상태 아키텍처 (DB = Single Source of Truth)</h2>
 * <ul>
 *   <li>{@code AVAILABLE}: DB에 저장된 기본 공석 상태</li>
 *   <li>{@code HOLD}: Redis Hash 선점 + DB HOLD 상태 동시 반영 (선점 즉시 DB 업데이트)</li>
 *   <li>{@code SOLD_OUT}: 결제 완료 후 DB 영구 상태 변경</li>
 * </ul>
 *
 * <h2>선점 만료 처리 흐름</h2>
 * <ol>
 *   <li>DB 트랜잭션 커밋 완료</li>
 *   <li>{@code @TransactionalEventListener(AFTER_COMMIT)}에서 Redisson Delayed Queue에 만료 메시지 등록</li>
 *   <li>TTL 경과 후 {@code SeatHoldExpiredHandler}가 메시지를 소비하여 DB를 AVAILABLE로 복구</li>
 *   <li>SSE로 해당 회차 구독 클라이언트에게 좌석 상태 변경 이벤트 전송</li>
 * </ol>
 *
 * <h2>트랜잭션 안전성</h2>
 * <ul>
 *   <li>Redis Lua Script(원자적) 성공 → DB HOLD 업데이트</li>
 *   <li>DB 실패 시 catch 블록에서 Redis 키 보상 삭제 후 예외 재throw</li>
 *   <li>Delayed Queue 등록은 반드시 DB 커밋 후(AFTER_COMMIT) 수행</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatOccupyManager {

    private final ConcertService concertService;
    private final TicketRepository ticketRepository;
    private final ScheduleSeatRepository scheduleSeatRepository;
    private final RedissonClient redissonClient;
    private final ApplicationEventPublisher eventPublisher;

    public static final long OCCUPY_TTL_SECONDS = 600;

    // ─── Lua Script: 원자적 좌석 선점 ────────────────────────────────────────
    // KEYS[1]: Hash 키 (seat:occupy:{concertId}:{scheduleId}:{seatNo})
    // KEYS[2]: ZSET 인덱스 키 (seat:occupy:index:{concertId}:{scheduleId})
    // ARGV[1]: userId, ARGV[2]: occupyToken, ARGV[3]: TTL(초), ARGV[4]: seatNumber, ARGV[5]: 만료 타임스탬프(ms)
    private static final String OCCUPY_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 1 then
              if redis.call('HGET', KEYS[1], 'userId') == ARGV[1] then
                redis.call('HSET', KEYS[1], 'occupyToken', ARGV[2])
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
                redis.call('ZADD', KEYS[2], tonumber(ARGV[5]), ARGV[4])
                return 1
              else
                return 0
              end
            else
              redis.call('HSET', KEYS[1], 'userId', ARGV[1], 'occupyToken', ARGV[2])
              redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
              redis.call('ZADD', KEYS[2], tonumber(ARGV[5]), ARGV[4])
              return 1
            end
            """;

    // ─── 좌석 선점 ────────────────────────────────────────────────────────────

    /**
     * 좌석을 임시 선점합니다.
     *
     * <p>Redis Lua Script로 원자적 선점을 수행한 뒤, DB 좌석 상태를 {@code HOLD}로 즉시 업데이트합니다.
     * DB 업데이트 실패 시 Redis 키를 보상 삭제합니다.
     * Delayed Queue 등록은 DB 커밋 성공 후 이벤트로 처리됩니다.
     */
    @Transactional
    public SeatOccupyResponse seatOccupy(Long concertId, Long scheduleId, String seatNumber, Long userId) {
        concertService.validateConcertScheduleMatch(concertId, scheduleId);
        concertService.validateScheduleBookable(scheduleId);

        String redisKey = generateSeatOccupyKey(concertId, scheduleId, seatNumber);
        String indexKey = generateSeatOccupyIndexKey(concertId, scheduleId);
        String occupyToken = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long expireAt = now + (OCCUPY_TTL_SECONDS * 1000);

        // 1. Redis 원자적 선점 (Lua Script - StringCodec으로 raw 값 전달)
        Long result = redissonClient.getScript(org.redisson.client.codec.StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                OCCUPY_SCRIPT,
                RScript.ReturnType.LONG,
                Arrays.asList(redisKey, indexKey),
                userId.toString(),
                occupyToken,
                String.valueOf(OCCUPY_TTL_SECONDS),
                seatNumber,
                String.valueOf(expireAt)
        );

        if (result == null || result == 0L) {
            throw new ServiceException(ErrorCode.SEAT_HELD_BY_OTHER_USER);
        }

        // 2. DB HOLD 상태 즉시 반영 (트랜잭션 내)
        try {
            ScheduleSeat seat = scheduleSeatRepository
                    .findWithLockByScheduleIdAndSeatNumber(scheduleId, seatNumber)
                    .orElseThrow(() -> new ServiceException(ErrorCode.SEAT_NOT_FOUND));

            if (seat.getSeatStatus() == SeatStatus.SOLD_OUT) {
                // Redis는 성공했지만 DB는 이미 SOLD_OUT → Redis 보상 삭제
                cleanupRedis(redisKey, indexKey, seatNumber);
                throw new ServiceException(ErrorCode.SEAT_ALREADY_SOLD);
            }

            // 이미 본인이 HOLD 중인 경우 (재선점) → DB 상태 유지
            if (seat.getSeatStatus() != SeatStatus.HOLD) {
                seat.updateSeatStatus(SeatStatus.HOLD);
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            // 예상치 못한 DB 오류 → Redis 보상 삭제
            cleanupRedis(redisKey, indexKey, seatNumber);
            throw e;
        }

        // 3. DB 커밋 후 Delayed Queue 등록 (AFTER_COMMIT 이벤트)
        eventPublisher.publishEvent(new SeatOccupiedEvent(concertId, scheduleId, seatNumber, OCCUPY_TTL_SECONDS));

        return SeatOccupyResponse.of(occupyToken, OCCUPY_TTL_SECONDS);
    }

    // ─── 좌석 선점 취소 ───────────────────────────────────────────────────────

    /**
     * 좌석 선점을 취소합니다. Redis 키 삭제 + DB AVAILABLE 복구.
     */
    @Transactional
    public void seatOccupyCancel(Long concertId, Long scheduleId, String seatNumber, Long userId) {
        concertService.validateConcertScheduleMatch(concertId, scheduleId);

        String redisKey = generateSeatOccupyKey(concertId, scheduleId, seatNumber);
        RMap<String, String> hashMap = redissonClient.getMap(redisKey);

        String occupyUserId = hashMap.get("userId");
        if (occupyUserId == null) {
            throw new ServiceException(ErrorCode.SEAT_HOLD_EXPIRED);
        }
        if (!occupyUserId.equals(userId.toString())) {
            throw new ServiceException(ErrorCode.SEAT_HELD_BY_OTHER_USER);
        }

        // Redis 삭제
        cleanupRedis(redisKey, generateSeatOccupyIndexKey(concertId, scheduleId), seatNumber);

        // DB AVAILABLE 복구
        scheduleSeatRepository
                .findWithLockByScheduleIdAndSeatNumber(scheduleId, seatNumber)
                .ifPresent(seat -> {
                    if (seat.getSeatStatus() == SeatStatus.HOLD) {
                        seat.updateSeatStatus(SeatStatus.AVAILABLE);
                    }
                });
    }

    // ─── 좌석 선택 페이지 조회 ────────────────────────────────────────────────

    /**
     * 좌석 선택 페이지 데이터를 조회합니다.
     *
     * <p>DB를 Single Source of Truth로 사용합니다.
     * DB의 {@code seatStatus}가 {@code HOLD} 또는 {@code SOLD_OUT}이면 그대로 반환합니다.
     * 별도의 Redis ZSET 조회가 필요 없습니다.
     */
    @Transactional(readOnly = true)
    public SeatSelectionResponse getSeatSelection(Long concertId, Long scheduleId, Long userId) {
        concertService.validateConcertScheduleMatch(concertId, scheduleId);
        concertService.validateScheduleBookable(scheduleId);

        long currentTicketCount = ticketRepository
                .countByUser_UserIdAndSchedule_ScheduleIdAndIsValidTrue(userId, scheduleId);
        if (currentTicketCount >= 3) {
            throw new ServiceException(ErrorCode.EXCEED_TICKET_LIMIT);
        }

        List<ScheduleSeat> seats = concertService.getScheduleSeats(scheduleId);
        Map<String, Integer> pricesMap = concertService.convertToPriceMap(seats);

        // DB 기반으로 직접 상태 반환 (Redis 조회 불필요)
        List<SeatDetailResponse> seatResponses = seats.stream()
                .map(seat -> new SeatDetailResponse(
                        seat.getSeatNumber(),
                        seat.getSeatStatus(),
                        seat.getGradeName()
                ))
                .toList();

        return SeatSelectionResponse.of(concertId, scheduleId, pricesMap, seatResponses);
    }

    // ─── Redis 보상 삭제 유틸 ─────────────────────────────────────────────────

    /**
     * Redis 선점 Hash 키와 ZSET 인덱스를 원자적으로 정리합니다.
     */
    public void cleanupRedis(String redisKey, String indexKey, String seatNumber) {
        redissonClient.getMap(redisKey).delete();
        redissonClient.getScoredSortedSet(indexKey).remove(seatNumber);
    }

    // ─── 키 생성 유틸 ─────────────────────────────────────────────────────────

    public static String generateSeatOccupyKey(Long concertId, Long scheduleId, String seatNumber) {
        return "seat:occupy:%d:%d:%s".formatted(concertId, scheduleId, seatNumber);
    }

    public static String generateSeatOccupyIndexKey(Long concertId, Long scheduleId) {
        return "seat:occupy:index:%d:%d".formatted(concertId, scheduleId);
    }
}
