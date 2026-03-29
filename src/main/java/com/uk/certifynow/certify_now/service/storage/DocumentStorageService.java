package com.uk.certifynow.certify_now.service.storage;

import java.io.InputStream;
import java.util.UUID;

/** Abstraction over object storage (MinIO, S3, local filesystem). */
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
   * Stores an arbitrary file (image or PDF) uploaded by a landlord and returns its public URL.
   *
   * <p>Unlike {@link #store}, this method accepts any MIME type and uses the document's own UUID as
   * the storage key prefix so the object path is stable before the entity is persisted.
   *
   * @param documentId the pre-generated UUID for the {@code Document} entity
   * @param filename original filename (used as the object key suffix)
   * @param content the raw file bytes
   * @param mimeType MIME type, e.g. {@code "application/pdf"} or {@code "image/jpeg"}
   * @return a publicly accessible URL for the stored file
   */
  String storeRaw(UUID documentId, String filename, byte[] content, String mimeType);

  /**
   * Retrieves the raw bytes for a document given its full storage URL.
   *
   * <p>Prefer {@link #streamByUrl} for download paths to avoid loading the entire file into heap
   * memory.
   *
   * @param storageUrl the URL returned by {@link #store} (or persisted in {@code
   *     Document.storageUrl})
   * @return the document bytes, or {@code null} if no document exists at this URL
   */
  byte[] retrieveByUrl(String storageUrl);

  /**
   * Opens a streaming {@link InputStream} for a document given its full storage URL.
   *
   * <p>The caller is responsible for closing the stream (use try-with-resources). Returns {@code
   * null} if no document exists at this URL.
   *
   * @param storageUrl the URL returned by {@link #store} (or persisted in {@code
   *     Document.storageUrl})
   * @return an open {@link InputStream} over the document bytes, or {@code null} if not found
   */
  InputStream streamByUrl(String storageUrl);
}
