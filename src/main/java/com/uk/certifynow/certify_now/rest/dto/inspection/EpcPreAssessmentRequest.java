package com.uk.certifynow.certify_now.rest.dto.inspection;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import java.util.List;

public record EpcPreAssessmentRequest(
        @JsonProperty("wall_type") String wallType,
        @JsonProperty("roof_insulation_depth_mm") Integer roofInsulationDepthMm,
        @JsonProperty("window_type") String windowType,
        @JsonProperty("boiler_make") String boilerMake,
        @JsonProperty("boiler_model") String boilerModel,
        @JsonProperty("boiler_age") String boilerAge,
        @JsonProperty("heating_controls") List<String> heatingControls,
        @JsonProperty("secondary_heating") String secondaryHeating,
        @JsonProperty("hot_water_cylinder_present") Boolean hotWaterCylinderPresent,
        @JsonProperty("cylinder_insulation") String cylinderInsulation,
        @JsonProperty("lighting_low_energy_count") Integer lightingLowEnergyCount,
        @Valid @JsonProperty("renewables") EpcRenewablesRequest renewables) {
}
