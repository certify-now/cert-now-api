package com.uk.certifynow.certify_now.service.pdf;

import java.util.UUID;

/** Generates and stores PDF certificates for issued {@code Certificate} records. */
public interface CertificatePdfService {

  /**
   * Generates the PDF for the given certificate, uploads it via {@link
   * com.uk.certifynow.certify_now.service.storage.DocumentStorageService}, and updates {@code
   * Certificate.documentUrl} and {@code GasSafetyRecord.qrCodeUrl}.
   *
   * <p>Implementations must retry on transient failures with exponential back-off before allowing
   * an exception to propagate to the caller.
   *
   * @param certificateId the {@link UUID} of the persisted {@code Certificate}
   */
  void generateAndStore(UUID certificateId);
}
