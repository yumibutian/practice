package jh.com.parse.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 单用户解析锁
 */
@Component
public class UserParseLock {

    private static final String KEY_PREFIX = "lock:parse:";

    private final StringRedisTemplate redis;
    private final Duration ttl;


    public UserParseLock(StringRedisTemplate redis, Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }


    @Autowired
    public UserParseLock(StringRedisTemplate redis,
                         @Value("${quiz.parse.single-user-lock-ttl-seconds}") long ttlSeconds) {
        this(redis, Duration.ofSeconds(ttlSeconds));
    }

    public boolean tryAcquire(Long userId) {
        Boolean ok = redis.opsForValue().setIfAbsent(KEY_PREFIX + userId, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }

    public void release(Long userId) {
        redis.delete(KEY_PREFIX + userId);
    }
}
