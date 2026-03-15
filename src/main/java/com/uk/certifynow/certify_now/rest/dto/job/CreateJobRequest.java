package com.uk.certifynow.certify_now.rest.dto.job;

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
    List<String> preferredDays,
    List<String> preferredTimeSlots) {

  /** Default urgency to STANDARD if not provided. */
  public String urgencyOrDefault() {
    return urgency == null ? "STANDARD" : urgency;
  }

  public String preferredDaysJoined() {
    return preferredDays == null || preferredDays.isEmpty()
        ? null
        : String.join(",", preferredDays);
  }

  public String preferredTimeSlotsJoined() {
    return preferredTimeSlots == null || preferredTimeSlots.isEmpty()
        ? null
        : String.join(",", preferredTimeSlots);
  }
}
