package com.uk.certifynow.certify_now.events.job;

import java.util.UUID;

/**
 * Published when a new job is created (POST /api/v1/jobs). Listeners: JobEventLogger (Phase 4
 * stub), MatchingJobListener (Phase 6).
 */
public class JobCreatedEvent {

  private final UUID jobId;
  private final UUID customerId;
  private final UUID propertyId;
  private final String certificateType;
  private final int totalPricePence;

  public JobCreatedEvent(
      final UUID jobId,
      final UUID customerId,
      final UUID propertyId,
      final String certificateType,
      final int totalPricePence) {
    this.jobId = jobId;
    this.customerId = customerId;
    this.propertyId = propertyId;
    this.certificateType = certificateType;
    this.totalPricePence = totalPricePence;
  }

  public UUID getJobId() {
    return jobId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public UUID getPropertyId() {
    return propertyId;
  }

  public String getCertificateType() {
    return certificateType;
  }

  public int getTotalPricePence() {
    return totalPricePence;
  }
}
