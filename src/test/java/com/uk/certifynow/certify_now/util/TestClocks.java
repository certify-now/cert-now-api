package com.uk.certifynow.certify_now.util;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class TestClocks {

  private TestClocks() {}

  /**
   * Returns a {@link Clock} whose {@code instant()} reads from {@code holder[0]}. Advance time by
   * updating {@code holder[0]} from the test.
   *
   * <pre>{@code
   * final Instant[] time = {TestConstants.FIXED_INSTANT};
   * final Clock clock = TestClocks.mutable(time);
   * // ... add entry that expires in 10 seconds ...
   * time[0] = time[0].plusSeconds(11); // advance clock
   * }</pre>
   */
  public static Clock mutable(final Instant[] holder) {
    return new Clock() {
      @Override
      public ZoneId getZone() {
        return ZoneOffset.UTC;
      }

      @Override
      public Clock withZone(final ZoneId zone) {
        return this;
      }

      @Override
      public Instant instant() {
        return holder[0];
      }
    };
  }
}
