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
public class JobMatchLogDTO {

  private UUID id;

  @Digits(integer = 6, fraction = 2)
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(type = "string", example = "79.08")
  private BigDecimal distanceMiles;

  @Digits(integer = 6, fraction = 3)
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(type = "string", example = "77.008")
  private BigDecimal matchScore;

  @NotNull private OffsetDateTime createdAt;

  @NotNull private OffsetDateTime offeredAt;

  private OffsetDateTime respondedAt;

  @Size(max = 20)
  private String response;

  @Size(max = 100)
  private String declineReason;

  @NotNull private UUID engineer;

  @NotNull private UUID job;
}
