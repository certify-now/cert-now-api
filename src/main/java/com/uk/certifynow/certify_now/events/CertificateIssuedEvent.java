package com.uk.certifynow.certify_now.events;

import java.util.UUID;

public class CertificateIssuedEvent {

  private final UUID jobId;
  private final UUID certificateId;
  private final UUID propertyId;
  private final String certificateType;

  public CertificateIssuedEvent(
      final UUID jobId,
      final UUID certificateId,
      final UUID propertyId,
      final String certificateType) {
    this.jobId = jobId;
    this.certificateId = certificateId;
    this.propertyId = propertyId;
    this.certificateType = certificateType;
  }

  public UUID getJobId() {
    return jobId;
  }

  public UUID getCertificateId() {
    return certificateId;
  }

  public UUID getPropertyId() {
    return propertyId;
  }

  public String getCertificateType() {
    return certificateType;
  }
}
