package com.uk.certifynow.certify_now.rest.dto.job;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full job detail response — returned by POST /api/v1/jobs and GET /api/v1/jobs/{id}.
 *
 * <p>Pricing, payment, and timestamps are nested objects to match the Flutter app contract.
 */
public record JobResponse(
    UUID id,
    String referenceNumber,
    UUID customerId,
    UUID propertyId,
    String propertyAddressSummary,
    UUID engineerId,
    String engineerName,
    String certificateType,
    String status,
    String urgency,
    String scheduledTimeSlot,
    LocalDate scheduledDate,
    Integer matchAttempts,
    String accessInstructions,
    String customerNotes,
    List<String> preferredDays,
    List<String> preferredTimeSlots,
    String cancelledBy,
    String cancellationReason,
    Pricing pricing,
    Payment payment,
    Timestamps timestamps) {

  public record Pricing(
      int basePricePence,
      int propertyModifierPence,
      int urgencyModifierPence,
      int discountPence,
      int totalPricePence,
      double commissionRate,
      int commissionPence,
      int engineerPayoutPence) {}

  public record Payment(UUID id, String status, String clientSecret, int amountPence) {}

  public record Timestamps(
      OffsetDateTime createdAt,
      OffsetDateTime matchedAt,
      OffsetDateTime acceptedAt,
      OffsetDateTime enRouteAt,
      OffsetDateTime startedAt,
      OffsetDateTime completedAt,
      OffsetDateTime certifiedAt,
      OffsetDateTime cancelledAt) {}
}
