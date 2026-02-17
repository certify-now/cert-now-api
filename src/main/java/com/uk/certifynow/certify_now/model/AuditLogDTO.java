package com.uk.certifynow.certify_now.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuditLogDTO {

  private UUID id;

  @NotNull private OffsetDateTime createdAt;

  private UUID actorId;

  @NotNull private UUID entityId;

  @NotNull
  @Size(max = 20)
  private String actorType;

  @NotNull
  @Size(max = 50)
  private String action;

  @NotNull
  @Size(max = 50)
  private String entityType;

  @Size(max = 255)
  private String ipAddress;

  private String userAgent;

  private String newValues;

  private String oldValues;
}
