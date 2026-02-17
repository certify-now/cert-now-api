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
public class CustomerProfileDTO {

  private UUID id;

  @Digits(integer = 5, fraction = 2)
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(type = "string", example = "73.08")
  private BigDecimal complianceScore;

  @NotNull
  @JsonProperty("isLettingAgent")
  private Boolean isLettingAgent;

  @NotNull private Integer totalProperties;

  @NotNull private OffsetDateTime createdAt;

  @NotNull private OffsetDateTime updatedAt;

  @Size(max = 255)
  private String companyName;

  @NotNull private String notificationPrefs;

  @NotNull private UUID user;
}
