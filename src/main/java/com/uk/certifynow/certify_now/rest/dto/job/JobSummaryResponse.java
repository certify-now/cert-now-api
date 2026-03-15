package com.uk.certifynow.certify_now.rest.dto.job;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Lightweight job summary for GET /api/v1/jobs (list view). */
public record JobSummaryResponse(
    UUID id,
    String referenceNumber,
    String certificateType,
    String status,
    String urgency,
    int totalPricePence,
    LocalDate scheduledDate,
    String scheduledTimeSlot,
    String propertyAddressSummary,
    String engineerName,
    OffsetDateTime createdAt,
    List<String> preferredDays,
    List<String> preferredTimeSlots) {}
