package com.uk.certifynow.certify_now.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageDTO {

  private UUID id;

  @NotNull
  @JsonProperty("isSystem")
  private Boolean isSystem;

  @NotNull private OffsetDateTime createdAt;

  private OffsetDateTime readAt;

  @NotNull private String body;

  @NotNull private UUID job;

  @NotNull private UUID sender;
}
