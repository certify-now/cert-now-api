package com.uk.certifynow.certify_now.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

  @NotNull
  private Boolean hasElectric;

  @NotNull
  private Boolean hasGasSupply;

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
  @Pattern(regexp = "^(FLAT|TERRACED|SEMI_DETACHED|DETACHED|BUNGALOW|MAISONETTE|COMMERCIAL|OTHER|HMO)$", message = "Invalid property type")
  private String propertyType;

  private String complianceStatus;

  @Size(max = 255)
  private String location;

  private UUID owner;
}
