package com.uk.certifynow.certify_now.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserConsentDTO {

  private UUID id;

  @NotNull private Boolean granted;

  @NotNull private OffsetDateTime createdAt;

  @NotNull private OffsetDateTime grantedAt;

  private OffsetDateTime revokedAt;

  @NotNull
  @Size(max = 255)
  private String consentType;

  @Size(max = 255)
  private String ipAddress;

  private String userAgent;

  @NotNull private UUID user;
}
