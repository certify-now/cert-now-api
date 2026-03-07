package com.uk.certifynow.certify_now.rest.dto.engineer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Request body for PUT /api/v1/engineer/availability. */
public record SetAvailabilityRequest(
    @NotNull(message = "slots is required") List<@Valid AvailabilitySlotRequest> slots) {}
