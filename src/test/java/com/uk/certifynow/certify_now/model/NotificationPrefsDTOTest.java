package com.uk.certifynow.certify_now.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationPrefsDTOTest {

  @Test
  void getExpiringSoonDays_multipleValues_returnsMax() {
    final NotificationPrefsDTO dto = new NotificationPrefsDTO();
    dto.setReminderDays(List.of(90, 60, 30));

    assertThat(dto.getExpiringSoonDays()).isEqualTo(90);
  }

  @Test
  void getExpiringSoonDays_singleValue_returnsThatValue() {
    final NotificationPrefsDTO dto = new NotificationPrefsDTO();
    dto.setReminderDays(List.of(60));

    assertThat(dto.getExpiringSoonDays()).isEqualTo(60);
  }

  @Test
  void getExpiringSoonDays_nullList_returnsDefault() {
    final NotificationPrefsDTO dto = new NotificationPrefsDTO();
    dto.setReminderDays(null);

    assertThat(dto.getExpiringSoonDays())
        .isEqualTo(NotificationPrefsDTO.DEFAULT_EXPIRING_SOON_DAYS);
  }

  @Test
  void getExpiringSoonDays_emptyList_returnsDefault() {
    final NotificationPrefsDTO dto = new NotificationPrefsDTO();
    dto.setReminderDays(Collections.emptyList());

    assertThat(dto.getExpiringSoonDays())
        .isEqualTo(NotificationPrefsDTO.DEFAULT_EXPIRING_SOON_DAYS);
  }

  @Test
  void defaultExpiringSoonDays_is60() {
    assertThat(NotificationPrefsDTO.DEFAULT_EXPIRING_SOON_DAYS).isEqualTo(60);
  }
}
