package com.back.global.security.jwt.repository;

import com.back.global.security.jwt.TokenHashUtil;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * JWT 블랙리스트 저장소.
 * <p>로그아웃/탈퇴 시 무효화된 AccessToken의 해시값을 TTL과 함께 저장합니다.
 */
@Repository
@RequiredArgsConstructor
public class BlacklistRepository {

    private final RedissonClient redissonClient;
    private static final String PREFIX = "auth:blacklist:";

    public void add(String accessToken, Duration ttl) {
        String tokenHash = TokenHashUtil.sha256(accessToken);
        RBucket<String> bucket = redissonClient.getBucket(PREFIX + tokenHash);
        bucket.set("blacklisted", ttl);
    }

    public boolean isBlacklisted(String accessToken) {
        String tokenHash = TokenHashUtil.sha256(accessToken);
        return redissonClient.getBucket(PREFIX + tokenHash).isExists();
    }
}