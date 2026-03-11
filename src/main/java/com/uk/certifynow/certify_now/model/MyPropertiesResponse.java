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
public class MyPropertiesResponse {

  private List<PropertyDTO> properties;
  private ComplianceHealthDTO complianceHealth;
}
