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
  @Size(max = 1024)
  private String storageUrl;

  @NotNull
  @Size(max = 255)
  private String fileName;

  @NotNull
  @Size(max = 100)
  private String mimeType;

  @NotNull private Long fileSizeBytes;

  @NotNull
  @JsonProperty("isVirusScanned")
  private Boolean isVirusScanned;

  private Boolean virusScanClean;

  @NotNull private UUID uploadedBy;

  private OffsetDateTime createdAt;

  private OffsetDateTime updatedAt;
}
