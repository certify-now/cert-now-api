package com.uk.certifynow.certify_now.rest.dto.job;

/**
 * Request body for PUT /api/v1/jobs/{id}/decline — ENGINEER declines an offer.
 */
public record DeclineJobRequest(String reason) {
}
