package com.back.global.security.jwt.repository

import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import com.back.global.security.jwt.RefreshTokenKeyType
import com.back.global.security.jwt.RefreshTokenValidationResult
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class RefreshTokenRepository(
    private val redissonClient: RedissonClient,
    @Value("\${custom.redis.refresh-token.prefix}") private val prefix: String,
    @Value("\${custom.redis.refresh-token.index-prefix}") private val indexPrefix: String
) {

    fun rotate(
        userId: Long,
        oldJti: String,
        requestRefreshTokenHash: String,
        newJti: String,
        newRefreshTokenHash: String,
        ttl: Duration
    ): RefreshTokenValidationResult {
        val oldTokenKey = generateKey(RefreshTokenKeyType.TOKEN, userId, oldJti)
        val newTokenKey = generateKey(RefreshTokenKeyType.TOKEN, userId, newJti)
        val indexKey = generateKey(RefreshTokenKeyType.INDEX, userId, null)

        val result: Long? = redissonClient.getScript(StringCodec.INSTANCE).eval(
            RScript.Mode.READ_WRITE,
            """
            local oldValue = redis.call('GET', KEYS[1])
            if not oldValue then
                return 0
            end
            if oldValue ~= ARGV[1] then
                return -1
            end
            redis.call('SET', KEYS[1], oldValue, 'EX', 5)
            redis.call('SREM', KEYS[3], ARGV[4])
            redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])
            redis.call('SADD', KEYS[3], ARGV[5])
            redis.call('EXPIRE', KEYS[3], ARGV[3])
            return 1
            """.trimIndent(),
            RScript.ReturnType.LONG,
            listOf(oldTokenKey, newTokenKey, indexKey),
            requestRefreshTokenHash,
            newRefreshTokenHash,
            ttl.toSeconds().toString(),
            oldJti,
            newJti
        )

        if (result == null) {
            throw ServiceException(ErrorCode.AUTH_REFRESH_TOKEN_ROTATION_FAILED)
        }

        return when (result.toInt()) {
            1 -> RefreshTokenValidationResult.SUCCESS
            -1 -> RefreshTokenValidationResult.MISMATCH
            0 -> RefreshTokenValidationResult.NOT_FOUND
            else -> throw ServiceException(ErrorCode.AUTH_REFRESH_TOKEN_ROTATION_FAILED)
        }
    }

    fun save(userId: Long, jti: String, refreshTokenHash: String, ttl: Duration) {
        val key = generateKey(RefreshTokenKeyType.TOKEN, userId, jti)
        val indexKey = generateKey(RefreshTokenKeyType.INDEX, userId, null)

        val bucket = redissonClient.getBucket<String>(key)
        bucket.set(refreshTokenHash, ttl)

        val index = redissonClient.getSet<String>(indexKey)
        index.add(jti)
        index.expire(ttl)
    }

    fun verify(userId: Long, jti: String, requestRefreshTokenHash: String): RefreshTokenValidationResult {
        val key = generateKey(RefreshTokenKeyType.TOKEN, userId, jti)
        val bucket = redissonClient.getBucket<String>(key)
        val savedHash = bucket.get()

        if (savedHash == null) {
            return RefreshTokenValidationResult.NOT_FOUND
        }
        if (savedHash != requestRefreshTokenHash) {
            return RefreshTokenValidationResult.MISMATCH
        }
        return RefreshTokenValidationResult.SUCCESS
    }

    fun delete(userId: Long, jti: String) {
        redissonClient.getBucket<String>(generateKey(RefreshTokenKeyType.TOKEN, userId, jti)).delete()
        val index = redissonClient.getSet<String>(generateKey(RefreshTokenKeyType.INDEX, userId, null))
        index.remove(jti)
    }

    fun deleteAllByUserId(userId: Long) {
        val indexKey = generateKey(RefreshTokenKeyType.INDEX, userId, null)
        val index = redissonClient.getSet<String>(indexKey)
        val jtis = index.readAll()

        if (jtis.isNullOrEmpty()) {
            return
        }

        jtis.map { jti -> generateKey(RefreshTokenKeyType.TOKEN, userId, jti) }
            .forEach { key -> redissonClient.getBucket<String>(key).delete() }

        index.delete()
    }

    private fun generateKey(type: RefreshTokenKeyType, userId: Long, jti: String?): String {
        return when (type) {
            RefreshTokenKeyType.TOKEN -> {
                require(!jti.isNullOrBlank()) { "jti is required for refresh token key" }
                "$prefix$userId:$jti"
            }
            RefreshTokenKeyType.INDEX -> {
                require(jti.isNullOrBlank()) { "jti must be empty for refresh token index key" }
                "$indexPrefix$userId"
            }
        }
    }
}
