package com.uk.certifynow.certify_now.rest.dto.inspection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyDetailsRequest(
    @NotBlank String tradingTitle,
    @NotBlank String addressLine1,
    String addressLine2,
    String addressLine3,
    @NotBlank @Size(max = 10) String postCode,
    @NotBlank @Size(max = 20) String gasSafeRegistrationNumber,
    @Size(max = 20) String companyPhone,
    String companyEmail) {}
