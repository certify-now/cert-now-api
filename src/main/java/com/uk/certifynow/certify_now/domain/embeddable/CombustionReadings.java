package com.uk.certifynow.certify_now.domain.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class CombustionReadings {

  @Column(name = "combustion_co_ppm", precision = 10, scale = 2)
  private BigDecimal coPpm;

  @Column(name = "combustion_co2_percentage", precision = 6, scale = 2)
  private BigDecimal co2Percentage;

  @Column(name = "combustion_co_to_co2_ratio", precision = 10, scale = 5)
  private BigDecimal coToCo2Ratio;

  @Column(name = "combustion_low", precision = 6, scale = 2)
  private BigDecimal combustionLow;

  @Column(name = "combustion_high", precision = 6, scale = 2)
  private BigDecimal combustionHigh;
}
