package com.uk.certifynow.certify_now.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProfileStatsDTO {

  private int totalProperties;

  private int validCerts;

  private int actionNeeded;
}
