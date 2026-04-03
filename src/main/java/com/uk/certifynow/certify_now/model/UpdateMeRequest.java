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

  @Pattern(
      regexp = "^\\+[1-9]\\d{7,14}$",
      message = "must be a valid phone number in international E.164 format (e.g. +447911123456)")
  private String phone;

  @Size(max = 512)
  private String avatarUrl;
}
