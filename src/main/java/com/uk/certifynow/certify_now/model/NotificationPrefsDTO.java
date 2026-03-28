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

  private Boolean push;

  private Boolean email;

  private Boolean sms;

  /** Days before expiry to send a renewal reminder, e.g. [90, 60, 30]. */
  private List<Integer> reminderDays;
}
