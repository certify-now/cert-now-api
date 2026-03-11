package com.uk.certifynow.certify_now.model;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PropertyComplianceItemDTO {

  private UUID propertyId;
  private String addressLine1;
  private String postcode;
  private String gasStatus;
  private String eicrStatus;
  private int propertyScore;
}
