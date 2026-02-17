package com.uk.certifynow.certify_now.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuration for providing a Clock bean for testable time-based operations. */
@Configuration
public class ClockConfig {

  /**
   * Provides a system default zone clock for production use. Can be overridden in tests with a
   * fixed clock for deterministic time-based testing.
   *
   * @return Clock instance
   */
  @Bean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }
}
