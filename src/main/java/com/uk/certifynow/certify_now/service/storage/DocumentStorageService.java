package com.uk.certifynow.certify_now.service.storage;

import java.util.UUID;

/** Abstraction over object storage (S3, GCS, local filesystem). */
public interface DocumentStorageService {

  /**
   * Stores the given bytes and returns the public URL at which the document can be retrieved.
   *
   * @param certificateId the certificate this document belongs to
   * @param certificateType e.g. {@code "GAS_SAFETY"}
   * @param content the PDF bytes
   * @return a publicly accessible URL for the stored document
   */
  String store(UUID certificateId, String certificateType, byte[] content);

  /**
   * Retrieves the PDF bytes for the given certificate.
   *
   * @param certificateId the certificate to retrieve
   * @return the PDF bytes, or {@code null} if no document exists for this certificate
   */
  byte[] retrieve(UUID certificateId);
}
