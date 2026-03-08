package com.uk.certifynow.certify_now.rest.dto.inspection;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EpcClientDetailsRequest(
    @NotBlank @JsonProperty("name") String name,
    @NotBlank @Email @JsonProperty("email") String email,
    @JsonProperty("telephone") String telephone,
    @JsonProperty("company") String company) {}
