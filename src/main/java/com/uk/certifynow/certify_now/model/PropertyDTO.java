package com.uk.certifynow.certify_now.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PropertyDTO {

  private UUID id;

  @Min(value = 0, message = "Bedrooms must be >= 0")
  private Integer bedrooms;

  @NotNull
  @Size(max = 2)
  private String country;

  @Digits(integer = 8, fraction = 2)
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(type = "string", example = "18.08")
  private BigDecimal floorAreaSqm;

  private Integer floors;

  private Integer gasApplianceCount;

  @NotNull private Boolean hasElectric;

  @NotNull private Boolean hasGasSupply;

  private Boolean isActive;

  private Integer yearBuilt;

  private OffsetDateTime createdAt;

  private OffsetDateTime updatedAt;

  @NotNull
  @Size(max = 10)
  @Pattern(regexp = "^[A-Z]{1,2}\\d[A-Z\\d]? ?\\d[A-Z]{2}$", message = "Invalid UK postcode")
  private String postcode;

  @Size(max = 20)
  private String uprn;

  @Size(max = 50)
  private String epcRegisterRef;

  @NotNull
  @Size(max = 100)
  private String city;

  @Size(max = 100)
  private String county;

  @NotNull
  @Size(max = 255)
  private String addressLine1;

  @Size(max = 255)
  private String addressLine2;

  @NotNull
  @Size(max = 255)
  @Pattern(
      regexp = "^(FLAT|TERRACED|SEMI_DETACHED|DETACHED|BUNGALOW|MAISONETTE|COMMERCIAL|OTHER|HMO)$",
      message = "Invalid property type")
  private String propertyType;

  private String complianceStatus;

  @Size(max = 255)
  private String location;

  private UUID owner;

  // ── Gas Safety Certificate ──────────────────────────────────────────────────
  private Boolean hasGasCertificate;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  private LocalDate gasExpiryDate;

  private String gasCertPdfName;

  /** True when a PDF file has been uploaded for this certificate. Read-only from API. */
  private Boolean hasGasCertPdf;

  // ── EICR ───────────────────────────────────────────────────────────────────
  private Boolean hasEicr;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  private LocalDate eicrExpiryDate;

  private String eicrCertPdfName;

  /** True when a PDF file has been uploaded for this certificate. Read-only from API. */
  private Boolean hasEicrCertPdf;

  // ── Computed compliance fields (read-only, populated by ComplianceService) ──

  @Schema(
      description =
          "Per-certificate gas compliance status: COMPLIANT, EXPIRING_SOON, EXPIRED, MISSING, NOT_APPLICABLE",
      accessMode = Schema.AccessMode.READ_ONLY)
  private String gasStatus;

  @Schema(
      description =
          "Per-certificate EICR compliance status: COMPLIANT, EXPIRING_SOON, EXPIRED, MISSING, NOT_APPLICABLE",
      accessMode = Schema.AccessMode.READ_ONLY)
  private String eicrStatus;

  @Schema(description = "Days until gas cert expires (null if not applicable)", accessMode = Schema.AccessMode.READ_ONLY)
  private Integer gasDaysUntilExpiry;

  @Schema(description = "Days until EICR expires (null if not applicable)", accessMode = Schema.AccessMode.READ_ONLY)
  private Integer eicrDaysUntilExpiry;

  @Schema(description = "List of recommended next actions for this property", accessMode = Schema.AccessMode.READ_ONLY)
  private List<String> nextActions;

  /** Byte arrays are never sent to the client — only the name/flag fields are. */
  @JsonIgnore
  private byte[] gasCertPdfBytes;

  @JsonIgnore
  private byte[] eicrCertPdfBytes;
}
