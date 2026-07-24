package com.back.global.security.interceptor

import com.back.domain.waiting.service.WaitingQueueManager
import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import com.back.global.requestcontext.RequestContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

@Component
class QueueInterceptor(
    private val redissonClient: RedissonClient,
    private val requestContext: RequestContext
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        if ("DELETE".equals(request.method, ignoreCase = true)) {
            return true
        }

        @Suppress("UNCHECKED_CAST")
        val pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<String, String>
        val scheduleIdStr = pathVariables?.get("scheduleId")
        if (scheduleIdStr == null || !scheduleIdStr.matches(Regex("^\\d+$"))) {
            throw ServiceException(ErrorCode.BAD_REQUEST)
        }
        val scheduleId = scheduleIdStr.toLong()

        val token = request.getHeader("X-Queue-Token")
        if (token.isNullOrBlank()) {
            throw ServiceException(ErrorCode.QUEUE_TOKEN_NOT_FOUND)
        }

        val actor = requestContext.actor ?: throw ServiceException(ErrorCode.AUTH_LOGIN_REQUIRED)
        val userId = actor.id

        val activeSet = redissonClient.getScoredSortedSet<String>(
            WaitingQueueManager.generateQueueActiveKey(scheduleId)
        )
        val score = activeSet.getScore(userId.toString())
        if (score == null || score < System.currentTimeMillis()) {
            throw ServiceException(ErrorCode.QUEUE_SESSION_EXPIRED)
        }

        val tokenBucket = redissonClient.getBucket<String>(
            WaitingQueueManager.generateActiveTokenKey(scheduleId, userId)
        )
        val storedToken = tokenBucket.get()
        if (token != storedToken) {
            throw ServiceException(ErrorCode.QUEUE_SESSION_EXPIRED)
        }

        return true
    }
}
