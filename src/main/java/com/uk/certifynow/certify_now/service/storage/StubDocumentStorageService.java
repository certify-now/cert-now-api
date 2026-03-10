package com.uk.certifynow.certify_now.service.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Local-filesystem document storage for development and testing.
 *
 * <p>Writes PDFs to {@code $TMPDIR/cert-now-pdfs/} and returns a {@code file://} URL. Replace with
 * an S3-backed implementation ({@code S3DocumentStorageService}) for production without changing
 * any callers.
 */
@Service
public class StubDocumentStorageService implements DocumentStorageService {

  private static final Logger log = LoggerFactory.getLogger(StubDocumentStorageService.class);

  private static final Path STORAGE_ROOT =
      Path.of(System.getProperty("java.io.tmpdir"), "cert-now-pdfs");

  @Override
  public String store(
      final UUID certificateId, final String certificateType, final byte[] content) {
    try {
      Files.createDirectories(STORAGE_ROOT);
      final Path target = STORAGE_ROOT.resolve(certificateId + ".pdf");
      Files.write(target, content);
      final String url = target.toUri().toString();
      log.info(
          "Stored {} certificate PDF: certificateId={} path={}",
          certificateType,
          certificateId,
          url);
      return url;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to store PDF for certificateId=" + certificateId, e);
    }
  }
}
