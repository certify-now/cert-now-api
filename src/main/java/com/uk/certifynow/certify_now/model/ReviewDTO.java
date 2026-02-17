package com.uk.certifynow.certify_now.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewDTO {

  private UUID id;

  private Integer communication;

  @NotNull
  @JsonProperty("isVisible")
  private Boolean isVisible;

  private Integer professionalism;

  private Integer punctuality;

  private Integer quality;

  @NotNull private Integer rating;

  @NotNull private OffsetDateTime createdAt;

  private String comment;

  @NotNull
  @Size(max = 255)
  private String direction;

  @NotNull private UUID job;

  @NotNull private UUID reviewee;

  @NotNull private UUID reviewer;
}
