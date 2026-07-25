package com.back.global.security.interceptor

import com.back.domain.waiting.service.WaitingQueueManager
import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import com.back.global.requestcontext.RequestContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.redisson.api.RedissonClient
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

@Component
class QueueInterceptor(
    private val redissonClient: RedissonClient,
    private val requestContext: RequestContext,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (request.method.equals("DELETE", ignoreCase = true)) {
            return true
        }

        val scheduleId = request.scheduleId()
        val token = request.getHeader("X-Queue-Token")
            ?.takeIf { it.isNotBlank() }
            ?: throw ServiceException(ErrorCode.QUEUE_TOKEN_NOT_FOUND)
        val userId = requestContext.actor?.id
            ?: throw ServiceException(ErrorCode.AUTH_LOGIN_REQUIRED)

        if (!hasActiveSession(scheduleId, userId)) {
            throw ServiceException(ErrorCode.QUEUE_SESSION_EXPIRED)
        }

        val storedToken = redissonClient
            .getBucket<String>(WaitingQueueManager.generateActiveTokenKey(scheduleId, userId))
            .get()

        if (token != storedToken) {
            throw ServiceException(ErrorCode.QUEUE_SESSION_EXPIRED)
        }

        return true
    }

    private fun hasActiveSession(scheduleId: Long, userId: Long): Boolean {
        val activeSet = redissonClient.getScoredSortedSet<String>(
            WaitingQueueManager.generateQueueActiveKey(scheduleId),
        )
        val expiresAt = activeSet.getScore(userId.toString()) ?: return false
        return expiresAt >= System.currentTimeMillis()
    }

    private fun HttpServletRequest.scheduleId(): Long {
        val pathVariables = getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<*, *>
        val scheduleId = pathVariables?.get("scheduleId")?.toString()

        return scheduleId
            ?.takeIf { it.matches(NUMBER_REGEX) }
            ?.toLong()
            ?: throw ServiceException(ErrorCode.BAD_REQUEST)
    }

    companion object {
        private val NUMBER_REGEX = Regex("^\\d+$")
    }
}
