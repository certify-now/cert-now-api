package com.uk.certifynow.certify_now.rest.dto.job;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * Request body for PUT /api/v1/jobs/{id}/accept — ENGINEER accepts and
 * schedules.
 */
public record AcceptJobRequest(
        @NotNull(message = "scheduledDate is required") @Future(message = "scheduledDate must be in the future") LocalDate scheduledDate,

        @NotNull(message = "scheduledTimeSlot is required") @Pattern(regexp = "MORNING|AFTERNOON|EVENING", message = "scheduledTimeSlot must be MORNING, AFTERNOON, or EVENING") String scheduledTimeSlot) {
}
