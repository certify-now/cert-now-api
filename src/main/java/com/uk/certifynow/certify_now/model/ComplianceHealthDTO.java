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
public class ComplianceHealthDTO {

  private int overallScore;
  private int totalProperties;
  private int compliantCount;
  private int expiringSoonCount;
  private int nonCompliantCount;
  private String summary;
  private List<PropertyComplianceItemDTO> items;
}
