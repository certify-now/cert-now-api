package com.uk.certifynow.certify_now.rest.dto.engineer;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Request body for POST /api/v1/engineer/qualifications. */
public record AddQualificationRequest(
    @NotNull(message = "type is required") String type,
    @NotNull(message = "registrationNumber is required") String registrationNumber,
    LocalDate issueDate,
    @NotNull(message = "expiryDate is required") LocalDate expiryDate,
    String schemeName,
    String documentUrl) {}
