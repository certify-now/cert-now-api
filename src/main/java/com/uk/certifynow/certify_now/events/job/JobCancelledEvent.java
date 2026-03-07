package com.uk.certifynow.certify_now.events.job;

import java.util.UUID;

/**
 * Published when a job is cancelled by customer, engineer, or admin. Listeners: JobEventLogger
 * (Phase 4), PaymentJobListener (Phase 8 — triggers refund).
 */
public class JobCancelledEvent {

  private final UUID jobId;
  private final String cancelledBy;
  private final String reason;
  private final int refundAmountPence;

  public JobCancelledEvent(
      final UUID jobId,
      final String cancelledBy,
      final String reason,
      final int refundAmountPence) {
    this.jobId = jobId;
    this.cancelledBy = cancelledBy;
    this.reason = reason;
    this.refundAmountPence = refundAmountPence;
  }

  public UUID getJobId() {
    return jobId;
  }

  public String getCancelledBy() {
    return cancelledBy;
  }

  public String getReason() {
    return reason;
  }

  public int getRefundAmountPence() {
    return refundAmountPence;
  }
}
