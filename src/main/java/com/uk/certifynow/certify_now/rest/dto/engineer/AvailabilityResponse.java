package com.uk.certifynow.certify_now.rest.dto.engineer;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Response DTO mapped from EngineerAvailability entity. */
public record AvailabilityResponse(
    UUID id,
    UUID engineerProfileId,
    Integer dayOfWeek,
    LocalTime startTime,
    LocalTime endTime,
    Boolean isAvailable,
    Boolean isRecurring,
    LocalDate overrideDate,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
