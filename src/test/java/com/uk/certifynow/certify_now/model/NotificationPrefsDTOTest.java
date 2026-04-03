package com.uk.certifynow.certify_now.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationPrefsDTOTest {

  @Test
  void expiringSoonThresholdDays_is30() {
    assertThat(NotificationPrefsDTO.EXPIRING_SOON_THRESHOLD_DAYS).isEqualTo(30);
  }
}
