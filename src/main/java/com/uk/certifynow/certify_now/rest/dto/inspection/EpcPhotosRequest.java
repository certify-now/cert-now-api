package com.uk.certifynow.certify_now.rest.dto.inspection;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record EpcPhotosRequest(
        @JsonProperty("exterior") List<String> exterior,
        @JsonProperty("boiler") List<String> boiler,
        @JsonProperty("boiler_data_plate") List<String> boilerDataPlate,
        @JsonProperty("heating_controls") List<String> heatingControls,
        @JsonProperty("radiators") List<String> radiators,
        @JsonProperty("windows") List<String> windows,
        @JsonProperty("loft") List<String> loft,
        @JsonProperty("hot_water_cylinder") List<String> hotWaterCylinder,
        @JsonProperty("renewables") List<String> renewables,
        @JsonProperty("other_evidence") List<String> otherEvidence) {
}
