package com.back.global.security.interceptor;

import com.back.domain.waiting.service.WaitingQueueManager;
import com.back.global.exception.ErrorCode;
import com.back.global.exception.ServiceException;
import com.back.global.requestcontext.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * 대기열 진입 세션 검증 인터셉터.
 *
 * <p>좌석 선택/선점 API 요청 시 진입열(Active Queue)에 유효한 세션이 존재하는지 확인합니다.
 * <ol>
 *   <li>진입열 ZSET에서 유저 ID의 Score(만료 타임스탬프) 조회 - 만료 여부 확인</li>
 *   <li>Redis에 저장된 진입 토큰과 요청 헤더의 X-Queue-Token 일치 여부 확인</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class QueueInterceptor implements HandlerInterceptor {

    private final RedissonClient redissonClient;
    private final RequestContext requestContext;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("DELETE".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String scheduleIdStr = (pathVariables != null) ? pathVariables.get("scheduleId") : null;
        if (scheduleIdStr == null || !scheduleIdStr.matches("^\\d+$")) {
            throw new ServiceException(ErrorCode.BAD_REQUEST);
        }
        Long scheduleId = Long.parseLong(scheduleIdStr);

        String token = request.getHeader("X-Queue-Token");
        if (token == null || token.isBlank()) {
            throw new ServiceException(ErrorCode.QUEUE_TOKEN_NOT_FOUND);
        }
        Long userId = requestContext.getActor().getId();

        // 진입열에 유저가 있는지, 만료되지 않았는지 확인
        RScoredSortedSet<String> activeSet = redissonClient.getScoredSortedSet(
                WaitingQueueManager.generateQueueActiveKey(scheduleId));
        Double score = activeSet.getScore(userId.toString());
        if (score == null || score < System.currentTimeMillis()) {
            throw new ServiceException(ErrorCode.QUEUE_SESSION_EXPIRED);
        }

        // 발급된 토큰과 일치하는지 확인
        RBucket<String> tokenBucket = redissonClient.getBucket(
                WaitingQueueManager.generateActiveTokenKey(scheduleId, userId));
        String storedToken = tokenBucket.get();
        if (!token.equals(storedToken)) {
            throw new ServiceException(ErrorCode.QUEUE_SESSION_EXPIRED);
        }

        return true;
    }
}
