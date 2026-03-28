package com.uk.certifynow.certify_now.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCustomerProfileRequest {

  @Size(max = 255)
  private String companyName;

  @JsonProperty("isLettingAgent")
  private Boolean isLettingAgent;
}
