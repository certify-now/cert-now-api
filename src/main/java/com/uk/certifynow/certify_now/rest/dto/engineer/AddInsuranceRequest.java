package com.uk.certifynow.certify_now.rest.dto.engineer;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Request body for POST /api/v1/engineer/insurance. */
public record AddInsuranceRequest(
    @NotNull(message = "policyType is required") String policyType,
    String provider,
    String policyNumber,
    @NotNull(message = "startDate is required") LocalDate startDate,
    @NotNull(message = "expiryDate is required") LocalDate expiryDate,
    @NotNull(message = "coverAmountPence is required") Long coverAmountPence,
    String documentUrl) {}
