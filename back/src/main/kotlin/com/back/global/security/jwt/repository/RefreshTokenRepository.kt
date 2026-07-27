package com.back.global.security.jwt.repository

import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import com.back.global.security.jwt.RefreshTokenKeyType
import com.back.global.security.jwt.RefreshTokenLuaScripts
import com.back.global.security.jwt.RefreshTokenValidationResult
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class RefreshTokenRepository(
    @Lazy
    private val redissonClient: RedissonClient,
    @Value("\${custom.redis.refresh-token.prefix}")
    private val prefix: String,
    @Value("\${custom.redis.refresh-token.index-prefix}")
    private val indexPrefix: String,
) {
    fun rotate(
        userId: Long,
        oldJti: String,
        requestRefreshTokenHash: String,
        newJti: String,
        newRefreshTokenHash: String,
        ttl: Duration,
    ): RefreshTokenValidationResult {
        val result = redissonClient.getScript(StringCodec.INSTANCE).eval<Long>(
            RScript.Mode.READ_WRITE,
            RefreshTokenLuaScripts.rotateScript(),
            RScript.ReturnType.LONG,
            listOf(
                generateKey(RefreshTokenKeyType.TOKEN, userId, oldJti),
                generateKey(RefreshTokenKeyType.TOKEN, userId, newJti),
                generateKey(RefreshTokenKeyType.INDEX, userId),
            ),
            requestRefreshTokenHash,
            newRefreshTokenHash,
            ttl.toSeconds().toString(),
            oldJti,
            newJti,
        ) ?: throw ServiceException(ErrorCode.AUTH_REFRESH_TOKEN_ROTATION_FAILED)

        return when (result.toInt()) {
            1 -> RefreshTokenValidationResult.SUCCESS
            -1 -> RefreshTokenValidationResult.MISMATCH
            0 -> RefreshTokenValidationResult.NOT_FOUND
            else -> throw ServiceException(ErrorCode.AUTH_REFRESH_TOKEN_ROTATION_FAILED)
        }
    }

    fun save(userId: Long, jti: String, refreshTokenHash: String, ttl: Duration) {
        redissonClient
            .getBucket<String>(generateKey(RefreshTokenKeyType.TOKEN, userId, jti))
            .set(refreshTokenHash, ttl)

        redissonClient
            .getSet<String>(generateKey(RefreshTokenKeyType.INDEX, userId))
            .apply {
                add(jti)
                expire(ttl)
            }
    }

    fun verify(userId: Long, jti: String, requestRefreshTokenHash: String): RefreshTokenValidationResult {
        val savedHash = redissonClient
            .getBucket<String>(generateKey(RefreshTokenKeyType.TOKEN, userId, jti))
            .get()

        return when {
            savedHash == null -> RefreshTokenValidationResult.NOT_FOUND
            savedHash != requestRefreshTokenHash -> RefreshTokenValidationResult.MISMATCH
            else -> RefreshTokenValidationResult.SUCCESS
        }
    }

    fun delete(userId: Long, jti: String) {
        redissonClient
            .getBucket<String>(generateKey(RefreshTokenKeyType.TOKEN, userId, jti))
            .delete()

        redissonClient
            .getSet<String>(generateKey(RefreshTokenKeyType.INDEX, userId))
            .remove(jti)
    }

    fun deleteAllByUserId(userId: Long) {
        val index = redissonClient.getSet<String>(generateKey(RefreshTokenKeyType.INDEX, userId))
        val jtis = index.readAll()

        if (jtis.isEmpty()) {
            return
        }

        jtis
            .map { jti -> generateKey(RefreshTokenKeyType.TOKEN, userId, jti) }
            .forEach { key -> redissonClient.getBucket<String>(key).delete() }

        index.delete()
    }

    private fun generateKey(type: RefreshTokenKeyType, userId: Long, jti: String? = null): String =
        when (type) {
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