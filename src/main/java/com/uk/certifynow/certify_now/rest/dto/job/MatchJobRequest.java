package com.uk.certifynow.certify_now.rest.dto.job;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for PUT /api/v1/jobs/{id}/match — ADMIN manually assigns an
 * engineer.
 *
 * <p>
 * TODO: Remove when MatchingService is built (Phase 6+). Temporary endpoint for
 * dev testing.
 */
public record MatchJobRequest(
        @NotNull(message = "engineerId is required") UUID engineerId) {
}
