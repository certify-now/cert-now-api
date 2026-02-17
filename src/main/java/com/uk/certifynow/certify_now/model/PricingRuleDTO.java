package com.uk.certifynow.certify_now.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PricingRuleDTO {

  private UUID id;

  @NotNull private Integer basePricePence;

  @NotNull private LocalDate effectiveFrom;

  private LocalDate effectiveTo;

  @NotNull
  @JsonProperty("isActive")
  private Boolean isActive;

  @NotNull private OffsetDateTime createdAt;

  private UUID createdBy;

  @Size(max = 50)
  private String region;

  @NotNull
  @Size(max = 255)
  private String certificateType;
}
