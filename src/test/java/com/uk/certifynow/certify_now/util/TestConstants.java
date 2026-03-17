package com.uk.certifynow.certify_now.util;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class TestConstants {

  public static final Instant FIXED_INSTANT = Instant.parse("2026-01-15T10:00:00Z");
  public static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
  public static final OffsetDateTime FIXED_NOW =
      OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);

  private TestConstants() {}
}
