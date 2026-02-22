package com.uk.certifynow.certify_now.model;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMeRequest {

  @Size(min = 2, max = 100)
  private String fullName;

  @Pattern(regexp = "^\\+44\\d{10}$", message = "must be a valid UK phone number")
  private String phone;

  @Size(max = 512)
  private String avatarUrl;
}
