package com.uk.certifynow.certify_now.rest.dto.engineer;

import jakarta.validation.constraints.NotNull;

/** Request body for PUT /api/v1/admin/engineers/{id}/verify-insurance/{iId}. */
public record VerifyInsuranceRequest(
    @NotNull(message = "verificationStatus is required") String verificationStatus) {}
