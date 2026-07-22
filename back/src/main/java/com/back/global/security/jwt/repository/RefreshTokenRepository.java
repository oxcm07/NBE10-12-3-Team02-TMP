package com.back.global.security.jwt.repository;

import com.back.global.exception.ErrorCode;
import com.back.global.exception.ServiceException;
import com.back.global.security.jwt.RefreshTokenKeyType;
import com.back.global.security.jwt.RefreshTokenValidationResult;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT Refresh Token 저장소.
 *
 * <p>Refresh Token Rotation 패턴을 지원합니다:
 * <ul>
 *   <li>토큰 발행 시 해시값을 Redis에 저장하고 인덱스(RSet)에 JTI를 등록합니다.</li>
 *   <li>Rotate 시 Lua Script로 원자적으로 구토큰 삭제 + 신토큰 등록을 처리합니다.</li>
 *   <li>탈퇴/로그아웃 시 인덱스를 통해 해당 유저의 모든 토큰을 일괄 삭제합니다.</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private final RedissonClient redissonClient;

    @Value("${custom.redis.refresh-token.prefix}")
    private String prefix;

    @Value("${custom.redis.refresh-token.index-prefix}")
    private String indexPrefix;

    /**
     * Refresh Token Rotation: 구토큰 검증 후 원자적으로 신토큰으로 교체합니다.
     */
    public RefreshTokenValidationResult rotate(
            Long userId,
            String oldJti,
            String requestRefreshTokenHash,
            String newJti,
            String newRefreshTokenHash,
            Duration ttl
    ) {
        String oldTokenKey = generateKey(RefreshTokenKeyType.TOKEN, userId, oldJti);
        String newTokenKey = generateKey(RefreshTokenKeyType.TOKEN, userId, newJti);
        String indexKey = generateKey(RefreshTokenKeyType.INDEX, userId, null);

        Long result = redissonClient.getScript(StringCodec.INSTANCE).eval(
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
                """,
                RScript.ReturnType.LONG,
                Arrays.asList(oldTokenKey, newTokenKey, indexKey),
                requestRefreshTokenHash,
                newRefreshTokenHash,
                String.valueOf(ttl.toSeconds()),
                oldJti,
                newJti
        );

        if (result == null) {
            throw new ServiceException(ErrorCode.AUTH_REFRESH_TOKEN_ROTATION_FAILED);
        }

        return switch (Math.toIntExact(result)) {
            case 1 -> RefreshTokenValidationResult.SUCCESS;
            case -1 -> RefreshTokenValidationResult.MISMATCH;
            case 0 -> RefreshTokenValidationResult.NOT_FOUND;
            default -> throw new ServiceException(ErrorCode.AUTH_REFRESH_TOKEN_ROTATION_FAILED);
        };
    }

    public void save(Long userId, String jti, String refreshTokenHash, Duration ttl) {
        String key = generateKey(RefreshTokenKeyType.TOKEN, userId, jti);
        String indexKey = generateKey(RefreshTokenKeyType.INDEX, userId, null);

        RBucket<String> bucket = redissonClient.getBucket(key);
        bucket.set(refreshTokenHash, ttl);

        RSet<String> index = redissonClient.getSet(indexKey);
        index.add(jti);
        index.expire(ttl);
    }

    public RefreshTokenValidationResult verify(Long userId, String jti, String requestRefreshTokenHash) {
        String key = generateKey(RefreshTokenKeyType.TOKEN, userId, jti);
        RBucket<String> bucket = redissonClient.getBucket(key);
        String savedHash = bucket.get();

        if (savedHash == null) {
            return RefreshTokenValidationResult.NOT_FOUND;
        }
        if (!savedHash.equals(requestRefreshTokenHash)) {
            return RefreshTokenValidationResult.MISMATCH;
        }
        return RefreshTokenValidationResult.SUCCESS;
    }

    public void delete(Long userId, String jti) {
        redissonClient.getBucket(generateKey(RefreshTokenKeyType.TOKEN, userId, jti)).delete();
        RSet<String> index = redissonClient.getSet(generateKey(RefreshTokenKeyType.INDEX, userId, null));
        index.remove(jti);
    }

    public void deleteAllByUserId(Long userId) {
        String indexKey = generateKey(RefreshTokenKeyType.INDEX, userId, null);
        RSet<String> index = redissonClient.getSet(indexKey);
        Set<String> jtis = index.readAll();

        if (jtis == null || jtis.isEmpty()) {
            return;
        }

        jtis.stream()
                .map(jti -> generateKey(RefreshTokenKeyType.TOKEN, userId, jti))
                .forEach(key -> redissonClient.getBucket(key).delete());

        index.delete();
    }

    private String generateKey(RefreshTokenKeyType type, Long userId, String jti) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        return switch (type) {
            case TOKEN -> {
                if (jti == null || jti.isBlank()) {
                    throw new IllegalArgumentException("jti is required for refresh token key");
                }
                yield prefix + userId + ":" + jti;
            }
            case INDEX -> {
                if (jti != null && !jti.isBlank()) {
                    throw new IllegalArgumentException("jti must be empty for refresh token index key");
                }
                yield indexPrefix + userId;
            }
        };
    }
}
