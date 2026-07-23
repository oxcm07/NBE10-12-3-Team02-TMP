package com.back.global

import jakarta.annotation.PreDestroy
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.redisson.spring.data.connection.RedissonConnectionFactory
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
import redis.embedded.RedisServer
import java.net.ServerSocket
import java.net.Socket

@TestConfiguration
class RedisTestConfig {

    @Bean
    @Primary
    fun redissonClient(): RedissonClient {
        if (isPortActive(6379)) {
            redisPort = 6379
        } else {
            try {
                if (redisServer == null) {
                    redisPort = findFreePort()
                    redisServer = RedisServer(redisPort)
                    redisServer?.start()
                }
            } catch (e: Exception) {
                throw RuntimeException("내장 레디스 구동 실패", e)
            }
        }

        val config = Config().apply {
            useSingleServer().address = "redis://127.0.0.1:$redisPort"
        }
        return Redisson.create(config)
    }

    @Bean
    @Primary
    fun redisConnectionFactory(redissonClient: RedissonClient): RedissonConnectionFactory {
        return RedissonConnectionFactory(redissonClient)
    }

    @Bean
    @Primary
    fun stringRedisTemplate(connectionFactory: RedissonConnectionFactory): StringRedisTemplate {
        return StringRedisTemplate().apply {
            setConnectionFactory(connectionFactory)
            keySerializer = StringRedisSerializer()
            valueSerializer = StringRedisSerializer()
            hashKeySerializer = StringRedisSerializer()
            hashValueSerializer = StringRedisSerializer()
            afterPropertiesSet()
        }
    }

    @PreDestroy
    fun stopRedis() {
        if (redisServer != null) {
            try {
                redisServer?.stop()
            } catch (ignored: Exception) {
            }
        }
    }

    companion object {
        private var redisServer: RedisServer? = null
        private var redisPort: Int = 0

        private fun findFreePort(): Int {
            return try {
                ServerSocket(0).use { it.localPort }
            } catch (e: Exception) {
                6370
            }
        }

        private fun isPortActive(port: Int): Boolean {
            return try {
                Socket("127.0.0.1", port).use { true }
            } catch (e: Exception) {
                false
            }
        }
    }
}
