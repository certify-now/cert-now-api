package com.uk.certifynow.certify_now.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CertificateDTO {

  private UUID id;

  private Integer epcScore;

  private LocalDate expiryAt;

  @NotNull private LocalDate issuedAt;

  private Integer validYears;

  @NotNull private OffsetDateTime createdAt;

  private OffsetDateTime shareTokenCreated;

  @NotNull private OffsetDateTime updatedAt;

  @Size(max = 64)
  private String shareToken;

  @Size(max = 100)
  private String certificateNumber;

  @NotNull
  @Size(max = 255)
  private String certificateType;

  @Size(max = 20)
  private String source;

  @Size(max = 512)
  private String epcRegistryUrl;

  @Size(max = 255)
  private String epcRating;

  @Size(max = 255)
  private String result;

  @NotNull
  @Size(max = 255)
  private String status;

  private String metadata;

  private UUID issuedByEngineer;

  private UUID job;

  @NotNull private UUID property;

  private UUID supersededBy;
}
