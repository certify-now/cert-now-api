package com.uk.certifynow.certify_now.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EpcAssessmentDTO {

  private UUID id;

  @NotNull private LocalDate assessmentDate;

  private Integer boilerAgeYears;

  @NotNull private Integer currentScore;

  private Integer environmentalImpact;

  private Boolean hasInsulatedTank;

  @NotNull private Boolean hasSolarPv;

  @NotNull private Boolean hasSolarThermal;

  private Integer lowEnergyLightingPct;

  private Integer numberOfFloors;

  private Integer potentialScore;

  @Digits(integer = 8, fraction = 2)
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(type = "string", example = "82.08")
  private BigDecimal totalFloorAreaSqm;

  @NotNull private OffsetDateTime createdAt;

  private OffsetDateTime lodgedAt;

  @NotNull private OffsetDateTime updatedAt;

  @NotNull
  @Size(max = 50)
  private String assessorAccreditation;

  @Size(max = 50)
  private String boilerType;

  @Size(max = 50)
  private String builtForm;

  @Size(max = 50)
  private String constructionDateRange;

  @Size(max = 50)
  private String epcRegisterRef;

  @Size(max = 50)
  private String roofInsulation;

  @Size(max = 50)
  private String roofType;

  @Size(max = 50)
  private String wallInsulation;

  @Size(max = 50)
  private String wallType;

  @Size(max = 50)
  private String windowFrame;

  @Size(max = 50)
  private String windowType;

  @Size(max = 100)
  private String heatingControls;

  @Size(max = 100)
  private String hotWaterSystem;

  @Size(max = 100)
  private String mainHeatingType;

  @Size(max = 100)
  private String schemeName;

  @NotNull
  @Size(max = 255)
  private String currentRating;

  @Size(max = 255)
  private String potentialRating;

  @NotNull private UUID job;
}
