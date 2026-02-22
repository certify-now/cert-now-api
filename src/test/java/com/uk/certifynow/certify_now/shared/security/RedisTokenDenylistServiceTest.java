package com.uk.certifynow.certify_now.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisTokenDenylistService")
class RedisTokenDenylistServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private RedisTokenDenylistService service;

  private static final String TEST_JTI = "test-jti-uuid-1234";
  private static final String REDIS_KEY = "jti:" + TEST_JTI;

  @BeforeEach
  void setUp() {
    service = new RedisTokenDenylistService(redisTemplate);
    // lenient() avoids UnnecessaryStubbingException for tests that don't use
    // opsForValue
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Nested
  @DisplayName("denyToken()")
  class DenyToken {

    @Test
    @DisplayName("should write jti to Redis with correct key and TTL")
    void shouldWriteJtiWithTtl() {
      service.denyToken(TEST_JTI, 900L);

      verify(valueOperations).set(REDIS_KEY, "1", Duration.ofSeconds(900));
    }

    @Test
    @DisplayName("should not throw when Redis is unavailable")
    void shouldNotThrowWhenRedisUnavailable() {
      when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

      // Should log error but not propagate exception
      service.denyToken(TEST_JTI, 900L);
      // If we get here, the test passes
    }
  }

  @Nested
  @DisplayName("isDenied()")
  class IsDenied {

    @Test
    @DisplayName("should return true when jti is in denylist")
    void shouldReturnTrueWhenDenied() {
      when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(true);

      assertThat(service.isDenied(TEST_JTI)).isTrue();
    }

    @Test
    @DisplayName("should return false when jti is not in denylist")
    void shouldReturnFalseWhenNotDenied() {
      when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(false);

      assertThat(service.isDenied(TEST_JTI)).isFalse();
    }

    @Test
    @DisplayName("should return false (fail-open) when Redis is unavailable")
    void shouldFailOpenWhenRedisUnavailable() {
      when(redisTemplate.hasKey(REDIS_KEY)).thenThrow(new RuntimeException("Redis down"));

      // Fail-open: must return false rather than throwing
      assertThat(service.isDenied(TEST_JTI)).isFalse();
    }

    @Test
    @DisplayName("should return false when Redis hasKey returns null")
    void shouldReturnFalseWhenRedisReturnsNull() {
      when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(null);

      assertThat(service.isDenied(TEST_JTI)).isFalse();
    }
  }
}
