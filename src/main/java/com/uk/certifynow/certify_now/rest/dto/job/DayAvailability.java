package com.uk.certifynow.certify_now.rest.dto.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** A single day's availability preference — which time slots the customer is available. */
public record DayAvailability(
    @NotBlank(message = "day is required") String day,
    @NotNull(message = "slots is required") List<String> slots) {}
