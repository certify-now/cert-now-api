package com.uk.certifynow.certify_now.rest.dto.inspection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InstallationDetailsRequest(
    String nameOrFlat,
    @NotBlank String addressLine1,
    String addressLine2,
    String addressLine3,
    @NotBlank @Size(max = 10) String postCode,
    @Size(max = 20) String telephone,
    String email) {}
