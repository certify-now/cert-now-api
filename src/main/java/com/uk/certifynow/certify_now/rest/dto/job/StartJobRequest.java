package com.uk.certifynow.certify_now.rest.dto.job;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Request body for PUT /api/v1/jobs/{id}/start — ENGINEER provides GPS to prove on-site. */
public record StartJobRequest(
    @NotNull(message = "latitude is required") BigDecimal latitude,
    @NotNull(message = "longitude is required") BigDecimal longitude) {}
