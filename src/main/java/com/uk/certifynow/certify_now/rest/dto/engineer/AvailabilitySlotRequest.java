package com.uk.certifynow.certify_now.rest.dto.engineer;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/** A single recurring availability slot within a SetAvailabilityRequest. */
public record AvailabilitySlotRequest(
    @NotNull(message = "dayOfWeek is required") Integer dayOfWeek,
    @NotNull(message = "startTime is required") LocalTime startTime,
    @NotNull(message = "endTime is required") LocalTime endTime,
    @NotNull(message = "isAvailable is required") Boolean isAvailable) {}
