package com.back.global.security.jwt.repository

import com.back.global.security.jwt.TokenHashUtil
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class BlacklistRepository(
    private val redissonClient: RedissonClient
) {
    fun add(accessToken: String, ttl: Duration) {
        val key = key(accessToken)
        redissonClient.getBucket<String>(key).set(BLACKLISTED_VALUE, ttl)
    }

    fun isBlacklisted(accessToken: String): Boolean =
        redissonClient.getBucket<String>(key(accessToken)).isExists

    private fun key(accessToken: String): String =
        PREFIX + TokenHashUtil.sha256(accessToken)

    companion object {
        private const val PREFIX = "auth:blacklist:"
        private const val BLACKLISTED_VALUE = "blacklisted"
    }
}
