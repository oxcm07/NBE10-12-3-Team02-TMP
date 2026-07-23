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
        val tokenHash = TokenHashUtil.sha256(accessToken)
        val bucket = redissonClient.getBucket<String>("$PREFIX$tokenHash")
        bucket.set("blacklisted", ttl)
    }

    fun isBlacklisted(accessToken: String): Boolean {
        val tokenHash = TokenHashUtil.sha256(accessToken)
        return redissonClient.getBucket<String>("$PREFIX$tokenHash").isExists
    }

    companion object {
        private const val PREFIX = "auth:blacklist:"
    }
}
