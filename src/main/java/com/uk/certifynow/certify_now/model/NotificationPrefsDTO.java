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

  /**
   * Fixed compliance threshold (in days) used to determine whether a certificate is "expiring
   * soon". This is a system-wide constant and is not derived from user preferences.
   */
  public static final int EXPIRING_SOON_THRESHOLD_DAYS = 30;

  private Boolean push;

  private Boolean email;

  private Boolean sms;

  /** Days before expiry to send a renewal reminder, e.g. [30, 14, 7]. */
  private List<Integer> reminderDays;
}
