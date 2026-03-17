package com.uk.certifynow.certify_now.service.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.uk.certifynow.certify_now.util.TestClocks;
import com.uk.certifynow.certify_now.util.TestConstants;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryTokenDenylistServiceTest {

  private static final Instant BASE = TestConstants.FIXED_INSTANT;

  private Clock clock;
  private InMemoryTokenDenylistService service;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(BASE, ZoneOffset.UTC);
    service = new InMemoryTokenDenylistService(clock);
  }

  @Test
  void denyToken_thenIsDenied_returnsTrue() {
    service.denyToken("jti-abc", 900L);
    assertThat(service.isDenied("jti-abc")).isTrue();
  }

  @Test
  void isDenied_unknownJti_returnsFalse() {
    assertThat(service.isDenied("unknown-jti")).isFalse();
  }

  @Test
  void isDenied_afterExpiry_returnsFalse() {
    final Instant[] time = {BASE};
    final var svc = new InMemoryTokenDenylistService(TestClocks.mutable(time));
    svc.denyToken("jti", 10L);
    assertThat(svc.isDenied("jti")).isTrue();
    time[0] = BASE.plusSeconds(11);
    assertThat(svc.isDenied("jti")).isFalse();
  }

  @Test
  void evictExpired_removesOnlyExpiredEntries() {
    final Instant[] time = {BASE};
    final InMemoryTokenDenylistService svc =
        new InMemoryTokenDenylistService(TestClocks.mutable(time));

    svc.denyToken("short-lived", 5L);
    svc.denyToken("long-lived", 3600L);

    time[0] = BASE.plusSeconds(10);
    svc.evictExpired();

    assertThat(svc.isDenied("short-lived")).isFalse();
    assertThat(svc.isDenied("long-lived")).isTrue();
  }

  @Test
  void denyToken_nullJti_noOp() {
    service.denyToken(null, 900L);
    assertThat(service.isDenied(null)).isFalse();
  }

  @Test
  void denyToken_blankJti_noOp() {
    service.denyToken("   ", 900L);
    assertThat(service.isDenied("   ")).isFalse();
  }

  @Test
  void denyToken_zeroTtl_noOp() {
    service.denyToken("zero-ttl-jti", 0L);
    assertThat(service.isDenied("zero-ttl-jti")).isFalse();
  }

  @Test
  void denyToken_negativeTtl_noOp() {
    service.denyToken("neg-ttl-jti", -1L);
    assertThat(service.isDenied("neg-ttl-jti")).isFalse();
  }

  @Test
  void clearAll_removesAllEntries() {
    service.denyToken("jti-1", 900L);
    service.denyToken("jti-2", 900L);
    service.clearAll();
    assertThat(service.isDenied("jti-1")).isFalse();
    assertThat(service.isDenied("jti-2")).isFalse();
  }
}
