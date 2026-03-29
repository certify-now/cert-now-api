package com.uk.certifynow.certify_now.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Local-filesystem document storage for development and testing.
 *
 * <p>Writes PDFs to {@code $TMPDIR/cert-now-pdfs/} and returns a {@code file://} URL. Active when
 * {@code app.storage.provider=stub} (or when the property is absent). Switch to {@code
 * app.storage.provider=minio} in any environment that has a real MinIO / S3 instance.
 */
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "stub", matchIfMissing = true)
public class StubDocumentStorageService implements DocumentStorageService {

  private static final Logger log = LoggerFactory.getLogger(StubDocumentStorageService.class);

  private static final Path STORAGE_ROOT =
      Path.of(System.getProperty("java.io.tmpdir"), "cert-now-pdfs");

  private static final Path UPLOAD_ROOT =
      Path.of(System.getProperty("java.io.tmpdir"), "cert-now-uploads");

  @Override
  public String store(
      final UUID certificateId, final String certificateType, final byte[] content) {
    try {
      Files.createDirectories(STORAGE_ROOT);
      final Path target = STORAGE_ROOT.resolve(certificateId + ".pdf");
      Files.write(target, content);
      final String url = target.toUri().toString();
      log.info(
          "Stored {} certificate PDF (stub): certificateId={} path={}",
          certificateType,
          certificateId,
          url);
      return url;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to store PDF for certificateId=" + certificateId, e);
    }
  }

  @Override
  public String storeRaw(
      final UUID documentId, final String filename, final byte[] content, final String mimeType) {
    try {
      Files.createDirectories(UPLOAD_ROOT);
      final String safe = (filename != null && !filename.isBlank()) ? filename : "upload";
      final Path target = UPLOAD_ROOT.resolve(documentId + "-" + safe);
      Files.write(target, content);
      final String url = target.toUri().toString();
      log.info("Stored uploaded document (stub): documentId={} path={}", documentId, url);
      return url;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to store document for documentId=" + documentId, e);
    }
  }

  @Override
  @Nullable
  public byte[] retrieveByUrl(final String storageUrl) {
    try {
      final Path target = Path.of(URI.create(storageUrl));
      if (!Files.exists(target)) {
        log.warn("PDF not found on filesystem for storageUrl={}", storageUrl);
        return null;
      }
      return Files.readAllBytes(target);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to retrieve PDF for storageUrl=" + storageUrl, e);
    }
  }

  @Override
  public InputStream streamByUrl(String storageUrl) {
    throw new UnsupportedOperationException(
        "streamByUrl is not supported in StubDocumentStorageService");
  }
}
