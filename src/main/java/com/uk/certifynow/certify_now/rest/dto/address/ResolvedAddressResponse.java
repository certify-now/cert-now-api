package com.uk.certifynow.certify_now.rest.dto.address;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Full structured address resolved from a suggestion id")
public record ResolvedAddressResponse(
    @Schema(example = "10 Downing Street") String addressLine1,
    @Schema(example = "Westminster") String addressLine2,
    @Schema(example = "London") String city,
    @Schema(example = "Greater London") String county,
    @Schema(example = "SW1A 2AA") String postcode,
    @Schema(example = "GB") String country,
    @Schema(description = "Unique Property Reference Number", example = "100023336956") String uprn,
    @Schema(description = "WGS84 latitude — used for PostGIS radius matching", example = "51.5034")
        Double latitude,
    @Schema(description = "WGS84 longitude — used for PostGIS radius matching", example = "-0.1276")
        Double longitude) {}
