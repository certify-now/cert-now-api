package com.uk.certifynow.certify_now.rest.dto.engineer;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Response DTO mapped from EngineerInsurance entity. */
public record InsuranceResponse(
    UUID id,
    UUID engineerProfileId,
    String policyType,
    String provider,
    String policyNumber,
    LocalDate startDate,
    LocalDate expiryDate,
    Long coverAmountPence,
    String documentUrl,
    String verificationStatus,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
