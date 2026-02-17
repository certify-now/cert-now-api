package com.uk.certifynow.certify_now.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StripeWebhookEventDTO {

  private UUID id;

  @NotNull private Boolean processed;

  @NotNull private OffsetDateTime createdAt;

  @NotNull
  @Size(max = 100)
  private String eventType;

  private String errorMessage;

  @NotNull
  @Size(max = 255)
  private String stripeEventId;

  private String payload;
}
