package com.uk.certifynow.certify_now.rest.dto.engineer;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/** Request body for POST /api/v1/engineer/availability/override. */
public record AvailabilityOverrideRequest(
    @NotNull(message = "overrideDate is required") LocalDate overrideDate,
    @NotNull(message = "startTime is required") LocalTime startTime,
    @NotNull(message = "endTime is required") LocalTime endTime,
    @NotNull(message = "isAvailable is required") Boolean isAvailable) {}
