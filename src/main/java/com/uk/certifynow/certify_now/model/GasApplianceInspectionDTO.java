package com.uk.certifynow.certify_now.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GasApplianceInspectionDTO {

  private UUID id;

  @NotNull private Integer applianceOrder;

  @Digits(integer = 8, fraction = 5)
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(type = "string", example = "19.0008")
  private BigDecimal co2ReadingPercent;

  @Digits(integer = 8, fraction = 5)
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(type = "string", example = "43.0008")
  private BigDecimal coReadingPercent;

  private Boolean flamePicturePass;

  @Digits(integer = 6, fraction = 2)
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(type = "string", example = "92.08")
  private BigDecimal operatingPressureMbar;

  @Digits(integer = 8, fraction = 5)
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(type = "string", example = "67.0008")
  private BigDecimal ratio;

  private Boolean safetyDeviceCorrect;

  private Boolean spillageTestPass;

  private Boolean ventilationPass;

  @NotNull private OffsetDateTime createdAt;

  @NotNull
  @Size(max = 50)
  private String applianceType;

  @Size(max = 50)
  private String flueType;

  @Size(max = 50)
  private String gcNumber;

  @NotNull
  @Size(max = 100)
  private String locationInProperty;

  @Size(max = 100)
  private String make;

  @Size(max = 100)
  private String model;

  @Size(max = 255)
  private String defectSeverity;

  private String defectsIdentified;

  @NotNull
  @Size(max = 255)
  private String result;

  @NotNull private UUID gasInspection;
}
