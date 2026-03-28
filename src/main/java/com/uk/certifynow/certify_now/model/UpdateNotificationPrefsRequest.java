package com.uk.certifynow.certify_now.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateNotificationPrefsRequest {

  private Boolean push;

  private Boolean email;

  private Boolean sms;

  @Size(max = 10)
  private List<@Min(1) @Max(365) Integer> reminderDays;
}
