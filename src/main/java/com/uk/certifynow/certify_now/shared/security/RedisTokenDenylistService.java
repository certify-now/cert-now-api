package com.uk.certifynow.certify_now.shared.security;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed implementation of {@link TokenDenylistService}.
 *
 * <p>Keys are stored as {@code jti:{jtiValue}} with a TTL equal to the remaining lifetime of the
 * access token. Redis garbage-collects expired keys automatically, so no cleanup job is needed.
 *
 * <p><b>Fail-open policy:</b> If Redis is unavailable, {@link #isDenied(String)} returns {@code
 * false} and logs a warning. This means a revoked access token may continue to work for up to 15
 * minutes (the JWT TTL) while Redis is down. See SECURITY NOTE in {@link JwtAuthenticationFilter}.
 */
@Service
public class RedisTokenDenylistService implements TokenDenylistService {

  private static final Logger log = LoggerFactory.getLogger(RedisTokenDenylistService.class);
  private static final String KEY_PREFIX = "jti:";

  private final StringRedisTemplate redisTemplate;

  public RedisTokenDenylistService(final StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public void denyToken(final String jti, final long ttlSeconds) {
    try {
      redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", Duration.ofSeconds(ttlSeconds));
    } catch (final Exception ex) {
      log.error(
          "SECURITY: Failed to write jti={} to Redis denylist — token may remain valid", jti, ex);
    }
  }

  @Override
  public boolean isDenied(final String jti) {
    try {
      return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    } catch (final Exception ex) {
      // SECURITY NOTE (fail-open): If Redis is unavailable we allow the request
      // through
      // rather than taking the service down. The access token TTL (15 min) limits
      // exposure.
      // Monitor Redis health via /actuator/health to detect outages quickly.
      log.warn("SECURITY: Redis denylist unavailable, failing-open for jti={}", jti, ex);
      return false;
    }
  }
}
