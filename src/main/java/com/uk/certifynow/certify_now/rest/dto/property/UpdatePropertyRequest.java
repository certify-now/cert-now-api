package com.uk.certifynow.certify_now.rest.dto.property;

import com.uk.certifynow.certify_now.service.PropertyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "Request body for updating an existing property")
public record UpdatePropertyRequest(
    @NotNull
        @Size(max = 255)
        @Schema(description = "First line of the property address", example = "14 Oak Lane")
        String addressLine1,
    @Size(max = 255)
        @Schema(description = "Second line of the property address (optional)", example = "Flat 2")
        String addressLine2,
    @NotNull @Size(max = 100) @Schema(description = "City", example = "London") String city,
    @Size(max = 100) @Schema(description = "County (optional)", example = "Greater London")
        String county,
    @NotNull
        @Size(max = 10)
        @Pattern(regexp = "^[A-Z]{1,2}\\d[A-Z\\d]? ?\\d[A-Z]{2}$", message = "Invalid UK postcode")
        @Schema(description = "UK postcode", example = "SW1A 2AA")
        String postcode,
    @NotNull @Size(max = 2) @Schema(description = "ISO 3166-1 alpha-2 country code", example = "GB")
        String country,
    @NotNull @Schema(description = "Property type") PropertyType propertyType,
    @NotNull @Schema(description = "Whether the property has a mains gas supply")
        Boolean hasGasSupply,
    @NotNull @Schema(description = "Whether the property has mains electricity")
        Boolean hasElectric,
    @Min(0) @Schema(description = "Number of bedrooms") Integer bedrooms,
    @Min(1) @Schema(description = "Number of floors") Integer floors,
    @Digits(integer = 8, fraction = 2)
        @Schema(description = "Floor area in square metres", example = "75.50")
        BigDecimal floorAreaSqm,
    @Min(1600) @Schema(description = "Year the property was built", example = "1985")
        Integer yearBuilt,
    @Min(0) @Schema(description = "Number of gas appliances (boilers, hobs, fires, etc.)")
        Integer gasApplianceCount) {}
