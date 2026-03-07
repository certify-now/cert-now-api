package com.uk.certifynow.certify_now.rest.dto.engineer;

import java.math.BigDecimal;

/** Request body for PUT /api/v1/engineer/profile. */
public record UpdateEngineerProfileRequest(
    String bio,
    String preferredCertTypes,
    String preferredJobTimes,
    BigDecimal serviceRadiusMiles,
    Integer maxDailyJobs) {}
