package com.uk.certifynow.certify_now.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NotificationPrefsDTO {

  /** Default threshold when no user preferences are available. */
  public static final int DEFAULT_EXPIRING_SOON_DAYS = 60;

  private Boolean push;

  private Boolean email;

  private Boolean sms;

  /** Days before expiry to send a renewal reminder, e.g. [90, 60, 30]. */
  private List<Integer> reminderDays;

  /**
   * Returns the maximum value from {@code reminderDays}, which is used as the "expiring soon"
   * threshold. Defaults to {@value #DEFAULT_EXPIRING_SOON_DAYS} when the list is null or empty.
   */
  public int getExpiringSoonDays() {
    if (reminderDays == null || reminderDays.isEmpty()) {
      return DEFAULT_EXPIRING_SOON_DAYS;
    }
    return reminderDays.stream()
        .mapToInt(Integer::intValue)
        .max()
        .orElse(DEFAULT_EXPIRING_SOON_DAYS);
  }
}
