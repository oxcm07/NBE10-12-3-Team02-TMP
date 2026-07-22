package com.back.global;

import jakarta.annotation.PreDestroy;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.embedded.RedisServer;

/**
 * 테스트용 Redis 설정.
 *
 * <p>포트 6379가 활성화(로컬 Redis 실행 중)된 경우 실 Redis에 연결하고,
 * 그렇지 않으면 내장 Redis(embedded-redis)를 사용합니다.
 * Redisson standalone 모드로 연결하여 통합 테스트가 실제 Redisson API와 함께 동작합니다.
 */
@TestConfiguration
public class RedisTestConfig {

    private static RedisServer redisServer;
    private static int redisPort;

    private static int findFreePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            return 6370;
        }
    }

    private static boolean isPortActive(int port) {
        try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Bean
    @Primary
    public RedissonClient redissonClient() {
        if (isPortActive(6379)) {
            redisPort = 6379;
        } else {
            try {
                if (redisServer == null) {
                    redisPort = findFreePort();
                    redisServer = new RedisServer(redisPort);
                    redisServer.start();
                }
            } catch (Exception e) {
                throw new RuntimeException("내장 레디스 구동 실패", e);
            }
        }

        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:" + redisPort);
        return Redisson.create(config);
    }

    @Bean
    @Primary
    public RedissonConnectionFactory redisConnectionFactory(RedissonClient redissonClient) {
        return new RedissonConnectionFactory(redissonClient);
    }

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate(RedissonConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @PreDestroy
    public void stopRedis() {
        if (redisServer != null) {
            try {
                redisServer.stop();
            } catch (Exception ignored) {
            }
        }
    }
}
