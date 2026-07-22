package com.back.domain.waiting.service;

import com.back.domain.waiting.dto.QueueStatusDto;
import com.back.global.exception.ErrorCode;
import com.back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/**
 * Redis 기반 대기열 상태 관리 컴포넌트.
 *
 * <p>Redisson 클라이언트를 통해 대기(Wait) / 진입(Active) 큐를 관리합니다.
 * <ul>
 *   <li>대기열 ({@code RScoredSortedSet}): 유저 ID를 Score(발급 순번) 기준으로 정렬</li>
 *   <li>진입열 ({@code RScoredSortedSet}): 유저 ID를 Score(만료 타임스탬프) 기준으로 정렬</li>
 *   <li>진입 토큰 ({@code RBucket}): 유저별 UUID 토큰, TTL 10분</li>
 *   <li>활성 회차 목록 ({@code RSet}): 스케줄러 루프 최소화용</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class WaitingQueueManager {

    private final RedissonClient redissonClient;

    private static final String WAIT_KEY_PREFIX = "queue:wait:schedule:";
    private static final String SEQUENCE_KEY_PREFIX = "queue:wait:sequence:schedule:";
    private static final String ACTIVE_TOKEN_KEY_PREFIX = "queue:active:token:";
    private static final String ACTIVE_SCHEDULES_KEY = "queue:active:schedules";

    // ─── 대기열 등록 ──────────────────────────────────────────────────────────

    /**
     * 대기열에 유저를 등록합니다. 이미 등록된 경우 기존 순번을 반환합니다.
     *
     * @return 현재 대기 순번 (1-based)
     */
    public Long registerWaiting(Long scheduleId, Long userId) {
        String waitKey = generateWaitKey(scheduleId);
        String seqKey = generateSequenceKey(scheduleId);
        String user = userId.toString();

        // 활성 회차 목록에 추가
        RSet<String> activeSchedules = redissonClient.getSet(ACTIVE_SCHEDULES_KEY);
        activeSchedules.add(scheduleId.toString());

        // Lua Script: 이미 등록된 경우 기존 순번 반환, 신규면 INCR 후 ZADD
        RScript script = redissonClient.getScript();
        Long rank = script.eval(
                RScript.Mode.READ_WRITE,
                """
                local exists = redis.call('ZSCORE', KEYS[1], ARGV[1])
                if not exists then
                  local sequence = redis.call('INCR', KEYS[2])
                  redis.call('ZADD', KEYS[1], sequence, ARGV[1])
                end
                local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
                if not rank then
                  return -1
                end
                return rank + 1
                """,
                RScript.ReturnType.INTEGER,
                Arrays.asList(waitKey, seqKey),
                user
        );

        if (rank == null || rank < 1) {
            throw new ServiceException(ErrorCode.WAITING_QUEUE_REGISTER_FAILED);
        }
        return rank;
    }

    // ─── 대기 순번 조회 ───────────────────────────────────────────────────────

    public Long showWaitingRank(Long scheduleId, Long userId) {
        RScoredSortedSet<String> waitSet = redissonClient.getScoredSortedSet(generateWaitKey(scheduleId));
        Integer rank = waitSet.rank(userId.toString());
        if (rank == null) {
            throw new ServiceException(ErrorCode.WAITING_QUEUE_NOT_FOUND);
        }
        return rank + 1L;
    }

    // ─── 대기열 취소 ──────────────────────────────────────────────────────────

    public boolean cancelWaiting(Long scheduleId, Long userId) {
        RScoredSortedSet<String> waitSet = redissonClient.getScoredSortedSet(generateWaitKey(scheduleId));
        return waitSet.remove(userId.toString());
    }

    public boolean cancelActiveUser(Long scheduleId, Long userId) {
        RScoredSortedSet<String> activeSet = redissonClient.getScoredSortedSet(generateQueueActiveKey(scheduleId));
        boolean removed = activeSet.remove(userId.toString());
        redissonClient.getBucket(generateActiveTokenKey(scheduleId, userId)).delete();
        return removed;
    }

    public void removeActiveUser(Long scheduleId, Long userId) {
        RScoredSortedSet<String> activeSet = redissonClient.getScoredSortedSet(generateQueueActiveKey(scheduleId));
        activeSet.remove(userId.toString());
        redissonClient.getBucket(generateActiveTokenKey(scheduleId, userId)).delete();
    }

    // ─── 만료된 진입자 청소 ───────────────────────────────────────────────────

    public long removeExpiredActiveUsers(Long scheduleId) {
        RScoredSortedSet<String> activeSet = redissonClient.getScoredSortedSet(generateQueueActiveKey(scheduleId));
        // Score가 현재 시간(ms)보다 작은 항목 = 만료된 유저
        int removed = activeSet.removeRangeByScore(0, true, System.currentTimeMillis(), true);
        return removed;
    }

    // ─── 진입 허가 ───────────────────────────────────────────────────────────

    /**
     * 대기열 앞쪽 유저를 진입열로 이동시킵니다.
     *
     * @param capacity  현재 진입 가능한 최대 인원
     * @param batchSize 한 번에 허가할 최대 인원
     * @param ttl       진입 토큰 유효 시간
     * @return 진입 허가된 userId 목록
     */
    public List<Long> addActiveUser(Long scheduleId, long capacity, int batchSize, Duration ttl) {
        String waitKey = generateWaitKey(scheduleId);
        String activeKey = generateQueueActiveKey(scheduleId);
        long now = System.currentTimeMillis();
        long expiredAt = now + ttl.toMillis();

        RScript script = redissonClient.getScript();
        List<String> userIds = script.eval(
                RScript.Mode.READ_WRITE,
                """
                redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[3])
                local activeCount = redis.call('ZCARD', KEYS[2])
                local availableSlots = tonumber(ARGV[1]) - activeCount
                if availableSlots <= 0 then
                  return {}
                end
                local popCount = math.min(availableSlots, tonumber(ARGV[2]))
                local users = redis.call('ZRANGE', KEYS[1], 0, popCount - 1)
                if #users == 0 then
                  return {}
                end
                redis.call('ZREM', KEYS[1], unpack(users))
                for _, u in ipairs(users) do
                  redis.call('ZADD', KEYS[2], ARGV[4], u)
                end
                return users
                """,
                RScript.ReturnType.MULTI,
                Arrays.asList(waitKey, activeKey),
                String.valueOf(capacity),
                String.valueOf(batchSize),
                String.valueOf(now),
                String.valueOf(expiredAt)
        );

        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userIds.stream().map(Long::valueOf).toList();
    }

    // ─── 토큰 발행 및 검증 ────────────────────────────────────────────────────

    public String issueToken(Long scheduleId, Long userId, Duration ttl) {
        String entryToken = UUID.randomUUID().toString();
        RBucket<String> bucket = redissonClient.getBucket(generateActiveTokenKey(scheduleId, userId));
        bucket.set(entryToken, ttl);
        return entryToken;
    }

    public String getActiveToken(Long scheduleId, Long userId) {
        RScoredSortedSet<String> activeSet = redissonClient.getScoredSortedSet(generateQueueActiveKey(scheduleId));
        Double score = activeSet.getScore(userId.toString());
        if (score == null || score <= System.currentTimeMillis()) {
            return null;
        }
        RBucket<String> bucket = redissonClient.getBucket(generateActiveTokenKey(scheduleId, userId));
        return bucket.get();
    }

    // ─── 큐 상태 조회 ────────────────────────────────────────────────────────

    public QueueStatusDto getQueueStatus(Long scheduleId) {
        RScoredSortedSet<String> waitSet = redissonClient.getScoredSortedSet(generateWaitKey(scheduleId));
        long totalWaitingCount = waitSet.size();
        if (totalWaitingCount == 0) {
            return new QueueStatusDto(0L, 0L);
        }

        // 대기열의 가장 첫 번째 항목(가장 작은 Score)의 Score에서 1을 빼면 이전 허가 번호
        Double firstScore = waitSet.firstScore();
        long currentAllowedSequence = (firstScore != null) ? firstScore.longValue() - 1 : 0L;
        return new QueueStatusDto(currentAllowedSequence, totalWaitingCount);
    }

    public Long getQueueSequence(Long scheduleId, Long userId) {
        RScoredSortedSet<String> waitSet = redissonClient.getScoredSortedSet(generateWaitKey(scheduleId));
        Double score = waitSet.getScore(userId.toString());
        return (score != null) ? score.longValue() : 0L;
    }

    // ─── 큐 비어있음 확인 ────────────────────────────────────────────────────

    public boolean isQueueEmpty(Long scheduleId) {
        RScoredSortedSet<String> waitSet = redissonClient.getScoredSortedSet(generateWaitKey(scheduleId));
        RScoredSortedSet<String> activeSet = redissonClient.getScoredSortedSet(generateQueueActiveKey(scheduleId));
        return waitSet.isEmpty() && activeSet.isEmpty();
    }

    // ─── 전체 대기열 삭제 ────────────────────────────────────────────────────

    public void clearWaitingQueue(Long scheduleId) {
        redissonClient.getScoredSortedSet(generateWaitKey(scheduleId)).delete();
    }

    // ─── 활성 회차 목록 관리 ──────────────────────────────────────────────────

    public Set<String> getActiveScheduleIds() {
        RSet<String> activeSchedules = redissonClient.getSet(ACTIVE_SCHEDULES_KEY);
        return activeSchedules.readAll();
    }

    public void removeFromActiveSchedules(String scheduleIdStr) {
        RSet<String> activeSchedules = redissonClient.getSet(ACTIVE_SCHEDULES_KEY);
        activeSchedules.remove(scheduleIdStr);
    }

    // ─── 키 생성 유틸 ────────────────────────────────────────────────────────

    private String generateWaitKey(Long scheduleId) {
        return WAIT_KEY_PREFIX + scheduleId;
    }

    private String generateSequenceKey(Long scheduleId) {
        return SEQUENCE_KEY_PREFIX + scheduleId;
    }

    public static String generateActiveTokenKey(Long scheduleId, Long userId) {
        return ACTIVE_TOKEN_KEY_PREFIX + scheduleId + ":" + userId;
    }

    public static String generateQueueActiveKey(Long scheduleId) {
        return "queue:active:schedule:%d".formatted(scheduleId);
    }
}
