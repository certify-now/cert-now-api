package com.uk.certifynow.certify_now.rest.dto.inspection;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EpcPropertyDetailsRequest(
    @NotBlank @JsonProperty("address_line1") String addressLine1,
    @JsonProperty("address_line2") String addressLine2,
    @JsonProperty("address_line3") String addressLine3,
    @NotBlank @JsonProperty("postcode") String postcode,
    @NotBlank @JsonProperty("property_type") String propertyType,
    @NotNull @JsonProperty("number_of_bedrooms") Integer numberOfBedrooms,
    @JsonProperty("year_built") Integer yearBuilt,
    @JsonProperty("floor_level") Integer floorLevel,
    @JsonProperty("access_notes") String accessNotes) {}
