package com.uk.certifynow.certify_now.rest.dto.job;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Request body for POST /api/v1/jobs — CUSTOMER books a job. */
public record CreateJobRequest(
    @NotNull(message = "propertyId is required") UUID propertyId,
    @NotNull(message = "certificateType is required") String certificateType,
    String urgency,
    String accessInstructions,
    String customerNotes,
    @Valid List<DayAvailability> preferredAvailability) {

  /** Default urgency to STANDARD if not provided. */
  public String urgencyOrDefault() {
    return urgency == null ? "STANDARD" : urgency;
  }
}
