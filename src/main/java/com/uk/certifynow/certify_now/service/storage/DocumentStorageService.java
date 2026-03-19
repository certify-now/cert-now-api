package com.uk.certifynow.certify_now.service.storage;

import java.util.UUID;

/** Abstraction over object storage (MinIO, S3, local filesystem). */
public interface DocumentStorageService {

  /**
   * Stores the given bytes and returns the public URL at which the document can be retrieved.
   *
   * @param certificateId  the certificate this document belongs to
   * @param certificateType e.g. {@code "GAS_SAFETY"}
   * @param content        the PDF bytes
   * @return a publicly accessible URL for the stored document
   */
  String store(UUID certificateId, String certificateType, byte[] content);

  /**
   * Retrieves the raw bytes for a document given its full storage URL.
   *
   * @param storageUrl the URL returned by {@link #store} (or persisted in {@code Document.storageUrl})
   * @return the document bytes, or {@code null} if no document exists at this URL
   */
  byte[] retrieveByUrl(String storageUrl);
}
