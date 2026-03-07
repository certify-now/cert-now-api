package com.uk.certifynow.certify_now.rest.dto.engineer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Response DTO for engineer profile endpoints. */
public record EngineerProfileResponse(
    UUID id,
    UUID userId,
    String status,
    String tier,
    String bio,
    String preferredCertTypes,
    String preferredJobTimes,
    BigDecimal serviceRadiusMiles,
    Integer maxDailyJobs,
    Boolean isOnline,
    BigDecimal acceptanceRate,
    BigDecimal avgRating,
    BigDecimal onTimePercentage,
    Integer totalJobsCompleted,
    Integer totalReviews,
    Boolean stripeOnboarded,
    String location,
    OffsetDateTime approvedAt,
    OffsetDateTime locationUpdatedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    int qualificationsCount,
    int insuranceCount,
    String availabilitySummary) {}
