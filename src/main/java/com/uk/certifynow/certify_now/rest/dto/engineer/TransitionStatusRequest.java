package com.uk.certifynow.certify_now.rest.dto.engineer;

import jakarta.validation.constraints.NotNull;

/** Request body for PUT /api/v1/admin/engineers/{id}/transition-status. */
public record TransitionStatusRequest(
    @NotNull(message = "targetStatus is required") String targetStatus) {}
