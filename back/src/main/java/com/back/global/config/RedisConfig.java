package com.back.global.config;

import org.redisson.api.RedissonClient;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 설정 클래스.
 *
 * <p>Redisson-Spring-Boot-Starter가 자동으로 {@link RedissonClient} Bean을 등록하며,
 * 해당 Bean을 기반으로 Spring Data Redis 호환 {@link RedissonConnectionFactory}를 구성합니다.
 * 이를 통해 Redis Sentinel 연결, Delayed Queue, RScript 등 Redisson 기능을 단일 클라이언트로 활용합니다.
 *
 * <p>Redisson 연결 설정(Sentinel 주소, masterName 등)은 프로파일별 YAML의
 * {@code spring.redis.redisson.config} 항목에서 관리됩니다.
 */
@Configuration
public class RedisConfig {

    /**
     * Redisson 기반 Spring Data Redis ConnectionFactory.
     * StringRedisTemplate, RedisTemplate 등이 이 Factory를 통해 Redis에 연결합니다.
     */
    @Bean
    public RedissonConnectionFactory redisConnectionFactory(RedissonClient redissonClient) {
        return new RedissonConnectionFactory(redissonClient);
    }

    /**
     * 문자열 키-값 전용 RedisTemplate (기존 StringRedisTemplate 대체 불필요, 호환성 유지용).
     * Redisson API (RBucket, RScoredSortedSet 등)를 직접 사용하는 것이 권장되지만,
     * 일부 레거시 연동이 필요한 경우를 위해 Bean으로 등록합니다.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedissonConnectionFactory redisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 범용 RedisTemplate (Object 직렬화).
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedissonConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
