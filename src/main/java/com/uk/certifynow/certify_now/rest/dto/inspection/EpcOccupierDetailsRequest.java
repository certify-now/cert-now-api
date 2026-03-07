package com.uk.certifynow.certify_now.rest.dto.inspection;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EpcOccupierDetailsRequest(
        @JsonProperty("name") String name,
        @JsonProperty("telephone") String telephone,
        @JsonProperty("email") String email,
        @JsonProperty("access_instructions") String accessInstructions) {
}
