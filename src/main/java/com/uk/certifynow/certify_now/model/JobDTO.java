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
public class JobDTO {

  private UUID id;

  @NotNull private Integer basePricePence;

  @NotNull private Integer commissionPence;

  @NotNull
  @Digits(integer = 4, fraction = 3)
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(type = "string", example = "5.008")
  private BigDecimal commissionRate;

  @NotNull private Integer discountPence;

  @NotNull private Integer engineerPayoutPence;

  @Digits(integer = 10, fraction = 7)
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(type = "string", example = "74.0008")
  private BigDecimal engineerStartLat;

  @Digits(integer = 10, fraction = 7)
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(type = "string", example = "94.0008")
  private BigDecimal engineerStartLng;

  @Digits(integer = 21, fraction = 0)
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(type = "string", example = "12")
  private BigDecimal estimatedDuration;

  @NotNull private Integer matchAttempts;

  @NotNull private Integer propertyModifierPence;

  private LocalDate scheduledDate;

  @NotNull private Integer totalPricePence;

  @NotNull private Integer urgencyModifierPence;

  private OffsetDateTime acceptedAt;

  private OffsetDateTime cancelledAt;

  private OffsetDateTime certifiedAt;

  private OffsetDateTime completedAt;

  @NotNull private OffsetDateTime createdAt;

  private OffsetDateTime enRouteAt;

  private OffsetDateTime escalatedAt;

  private OffsetDateTime matchedAt;

  private OffsetDateTime scheduledAt;

  private OffsetDateTime startedAt;

  @NotNull private OffsetDateTime updatedAt;

  @NotNull
  @Size(max = 20)
  private String referenceNumber;

  @Size(max = 20)
  private String scheduledTimeSlot;

  private String accessInstructions;

  private String cancellationReason;

  @Size(max = 255)
  private String cancelledBy;

  @NotNull
  @Size(max = 255)
  private String certificateType;

  private String customerNotes;

  @NotNull
  @Size(max = 255)
  private String status;

  @NotNull
  @Size(max = 255)
  private String urgency;

  @NotNull private UUID customer;

  private UUID engineer;

  @NotNull private UUID property;
}
