package com.uk.certifynow.certify_now.events;

import com.uk.certifynow.certify_now.service.job.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CertificateIssuedEventListener {

  private static final Logger log = LoggerFactory.getLogger(CertificateIssuedEventListener.class);

  private final JobService jobService;

  public CertificateIssuedEventListener(final JobService jobService) {
    this.jobService = jobService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCertificateIssued(final CertificateIssuedEvent event) {
    log.info(
        "Certificate issued: certificateId={}, jobId={}, propertyId={}, type={}",
        event.getCertificateId(),
        event.getJobId(),
        event.getPropertyId(),
        event.getCertificateType());

    // Transition job from COMPLETED -> CERTIFIED
    jobService.certifyJob(event.getJobId());

    // TODO: Update property compliance status when PropertyService supports it
    // propertyService.updateComplianceStatus(event.getPropertyId(),
    // event.getCertificateType(), "VALID");
  }
}
