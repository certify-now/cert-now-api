package com.uk.certifynow.certify_now.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Downstream hook for certificate-issued notifications (e.g. property compliance updates).
 *
 * <p>The COMPLETED → CERTIFIED job status transition and its audit-history row are both committed
 * atomically inside the inspection submission transaction; this listener is intentionally
 * <em>not</em> responsible for that transition.
 */
@Component
public class CertificateIssuedEventListener {

  private static final Logger log = LoggerFactory.getLogger(CertificateIssuedEventListener.class);

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCertificateIssued(final CertificateIssuedEvent event) {
    log.info(
        "Certificate issued (downstream hook): certificateId={}, jobId={}, propertyId={}, type={}",
        event.getCertificateId(),
        event.getJobId(),
        event.getPropertyId(),
        event.getCertificateType());

    // TODO: Update property compliance status when PropertyService supports it
    // propertyService.updateComplianceStatus(event.getPropertyId(),
    // event.getCertificateType(), "VALID");
  }
}
