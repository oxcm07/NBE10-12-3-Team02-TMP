package com.back.global.ratelimit

import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import com.back.global.security.SecurityUser
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import jakarta.annotation.PostConstruct
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import java.time.Duration

@Component
class RateLimitInterceptor(
    @Value("\${app.rate-limit.seat-status.capacity:5}") private val capacity: Int,
    @Value("\${app.rate-limit.seat-status.refill-tokens:5}") private val refillTokens: Int,
    @Value("\${app.rate-limit.seat-status.refill-seconds:1}") private val refillSeconds: Long,
    @Value("\${app.rate-limit.seat-status.expire-after-access-minutes:10}") private val expireAfterAccessMinutes: Long,
    @Value("\${app.rate-limit.seat-status.maximum-size:10000}") private val maximumSize: Long
) : HandlerInterceptor {

    private lateinit var buckets: Cache<String, Bucket>

    @PostConstruct
    fun init() {
        buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(expireAfterAccessMinutes))
            .maximumSize(maximumSize)
            .build()
    }

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val bucketKey = resolveBucketKey(request)
        val bucket = buckets.get(bucketKey) { createBucket() }

        if (bucket.tryConsume(1)) {
            return true
        }

        throw ServiceException(ErrorCode.TOO_MANY_REQUESTS)
    }

    private fun createBucket(): Bucket {
        val limit = Bandwidth.classic(
            capacity.toLong(),
            Refill.intervally(refillTokens.toLong(), Duration.ofSeconds(refillSeconds))
        )

        return Bucket.builder()
            .addLimit(limit)
            .build()
    }

    private fun resolveBucketKey(request: HttpServletRequest): String {
        val scheduleId = extractScheduleId(request)
        val authentication = SecurityContextHolder.getContext().authentication

        if (authentication != null && authentication.principal is SecurityUser) {
            val securityUser = authentication.principal as SecurityUser
            return "user:${securityUser.id}:schedule:$scheduleId:seat-status"
        }

        return "ip:${getClientIp(request)}:schedule:$scheduleId:seat-status"
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractScheduleId(request: HttpServletRequest): String {
        val pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<String, String>
            ?: throw ServiceException(ErrorCode.BAD_REQUEST)

        val scheduleId = pathVariables["scheduleId"]
        if (scheduleId.isNullOrBlank()) {
            throw ServiceException(ErrorCode.BAD_REQUEST)
        }

        return scheduleId
    }

    private fun getClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        if (!forwarded.isNullOrBlank()) {
            return forwarded.split(",")[0].trim()
        }

        val realIp = request.getHeader("X-Real-IP")
        if (!realIp.isNullOrBlank()) {
            return realIp.trim()
        }

        return request.remoteAddr
    }
}
