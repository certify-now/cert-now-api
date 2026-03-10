package com.uk.certifynow.certify_now.events;

import com.uk.certifynow.certify_now.service.pdf.CertificatePdfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Downstream hook for certificate-issued notifications.
 *
 * <p>Runs after the inspection transaction commits ({@code AFTER_COMMIT}) so the {@code
 * Certificate} and {@code GasSafetyRecord} rows are guaranteed to be visible in a new transaction.
 * The {@code @Async("pdfTaskExecutor")} annotation offloads PDF generation to a dedicated thread
 * pool, keeping the HTTP response fast and ensuring the original transaction is not held open
 * during rendering or storage I/O.
 *
 * <p>PDF/storage failures are caught and logged here. The original inspection/cert issuance is
 * already committed at this point, so there is nothing to roll back. {@link
 * CertificatePdfService#generateAndStore} retries up to 3 times with exponential back-off before
 * the exception propagates to this handler.
 */
@Component
public class CertificateIssuedEventListener {

  private static final Logger log = LoggerFactory.getLogger(CertificateIssuedEventListener.class);

  private final CertificatePdfService certificatePdfService;

  public CertificateIssuedEventListener(final CertificatePdfService certificatePdfService) {
    this.certificatePdfService = certificatePdfService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async("pdfTaskExecutor")
  public void onCertificateIssued(final CertificateIssuedEvent event) {
    log.info(
        "Certificate issued (post-commit): certificateId={}, jobId={}, propertyId={}, type={}",
        event.getCertificateId(),
        event.getJobId(),
        event.getPropertyId(),
        event.getCertificateType());

    if (!"GAS_SAFETY".equals(event.getCertificateType())) {
      log.debug("Skipping PDF generation for unsupported type: {}", event.getCertificateType());
      return;
    }

    try {
      certificatePdfService.generateAndStore(event.getCertificateId());
    } catch (Exception e) {
      log.error(
          "PDF generation failed for certificateId={} after all retry attempts. "
              + "The certificate record remains valid — document URL will be absent until retried manually.",
          event.getCertificateId(),
          e);
    }
  }
}
