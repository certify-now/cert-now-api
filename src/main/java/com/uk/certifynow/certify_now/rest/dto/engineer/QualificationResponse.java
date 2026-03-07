package com.uk.certifynow.certify_now.rest.dto.engineer;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Response DTO mapped from EngineerQualification entity. */
public record QualificationResponse(
    UUID id,
    UUID engineerProfileId,
    String type,
    String registrationNumber,
    LocalDate issueDate,
    LocalDate expiryDate,
    String schemeName,
    String documentUrl,
    String verificationStatus,
    Boolean externalVerified,
    OffsetDateTime verifiedAt,
    UUID verifiedBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
