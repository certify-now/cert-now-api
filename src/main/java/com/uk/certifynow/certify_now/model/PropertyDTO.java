package com.uk.certifynow.certify_now.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
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

    @NotNull
    @JsonProperty("isActive")
    private Boolean isActive;

    private Integer yearBuilt;

    @NotNull
    private OffsetDateTime createdAt;

    @NotNull
    private OffsetDateTime updatedAt;

    @NotNull
    @Size(max = 10)
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
    private String propertyType;

    @NotNull
    private String complianceStatus;

    @Size(max = 255)
    private String location;

    @NotNull
    private UUID owner;

}
