package com.uk.certifynow.certify_now.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class EngineerProfileDTO {

  private UUID id;

  @NotNull
  @Digits(integer = 5, fraction = 2)
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(type = "string", example = "95.08")
  private BigDecimal acceptanceRate;

  @NotNull
  @Digits(integer = 3, fraction = 2)
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(type = "string", example = "5.08")
  private BigDecimal avgRating;

  @NotNull
  @JsonProperty("isOnline")
  private Boolean isOnline;

  @NotNull private Integer maxDailyJobs;

  @NotNull
  @Digits(integer = 5, fraction = 2)
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(type = "string", example = "38.08")
  private BigDecimal onTimePercentage;

  @NotNull
  @Digits(integer = 4, fraction = 1)
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(type = "string", example = "93.8")
  private BigDecimal serviceRadiusMiles;

  @NotNull private Boolean stripeOnboarded;

  @NotNull private Integer totalJobsCompleted;

  @NotNull private Integer totalReviews;

  private OffsetDateTime approvedAt;

  @NotNull private OffsetDateTime createdAt;

  private OffsetDateTime dbsCheckedAt;

  private OffsetDateTime idVerifiedAt;

  private OffsetDateTime insuranceVerifiedAt;

  private OffsetDateTime locationUpdatedAt;

  private OffsetDateTime trainingCompletedAt;

  @NotNull private OffsetDateTime updatedAt;

  @Size(max = 50)
  private String dbsCertificateNumber;

  @Size(max = 50)
  private String dbsStatus;

  private String bio;

  @NotNull
  @Size(max = 255)
  private String status;

  @Size(max = 255)
  private String stripeAccountId;

  @NotNull
  @Size(max = 255)
  private String tier;

  @Size(max = 255)
  private String location;

  private String preferredCertTypes;

  private String preferredJobTimes;

  @NotNull private UUID user;
}
