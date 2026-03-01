package com.uk.certifynow.certify_now.rest.dto.job;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for PUT /api/v1/jobs/{id}/cancel.
 */
public record CancelJobRequest(
        @NotBlank(message = "reason is required") String reason) {
}
