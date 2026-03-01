package com.uk.certifynow.certify_now.rest.dto.job;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row from the job status history trail.
 * Returned by GET /api/v1/jobs/{id}/history.
 */
public record JobStatusHistoryResponse(
        UUID id,
        String fromStatus,
        String toStatus,
        UUID actorId,
        String actorType,
        String reason,
        Object metadata,
        OffsetDateTime createdAt) {
}
