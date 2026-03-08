package com.uk.certifynow.certify_now.rest.dto.inspection;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EpcRenewablesRequest(
    @JsonProperty("solar_pv") Boolean solarPv,
    @JsonProperty("solar_thermal") Boolean solarThermal,
    @JsonProperty("heat_pump") Boolean heatPump) {}
