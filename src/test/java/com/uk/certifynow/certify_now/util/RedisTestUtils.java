package com.uk.certifynow.certify_now.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisTestUtils {

  private static final String JTI_PREFIX = "jti:";

  private final StringRedisTemplate redisTemplate;

  public RedisTestUtils(final StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public boolean jtiIsDenylisted(final String jti) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(JTI_PREFIX + jti));
  }

  public Long getJtiTtl(final String jti) {
    return redisTemplate.getExpire(JTI_PREFIX + jti);
  }

  public void flushAll() {
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
  }
}
