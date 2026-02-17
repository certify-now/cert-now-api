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
public class DocumentDTO {

  private UUID id;

  @NotNull
  @JsonProperty("isVirusScanned")
  private Boolean isVirusScanned;

  private Boolean virusScanClean;

  @NotNull private OffsetDateTime createdAt;

  @NotNull private Long fileSizeBytes;

  private UUID relatedId;

  @Size(max = 50)
  private String relatedEntity;

  @NotNull
  @Size(max = 100)
  private String mimeType;

  @NotNull
  @Size(max = 100)
  private String s3Bucket;

  @NotNull
  @Size(max = 512)
  private String s3Key;

  @NotNull
  @Size(max = 255)
  private String documentType;

  @NotNull
  @Size(max = 255)
  private String fileName;

  @NotNull private UUID owner;
}
