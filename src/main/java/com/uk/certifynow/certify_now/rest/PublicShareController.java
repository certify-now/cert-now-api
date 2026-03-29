package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.service.PublicShareService;
import com.uk.certifynow.certify_now.service.PublicShareService.DocumentMeta;
import com.uk.certifynow.certify_now.service.PublicShareService.DocumentMetaWithLabel;
import com.uk.certifynow.certify_now.service.PublicShareService.SharePageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Slf4j
@RestController
@RequestMapping("/share")
@Tag(name = "Public Share", description = "Public certificate share page endpoints")
public class PublicShareController {

  private final PublicShareService publicShareService;

  public PublicShareController(final PublicShareService publicShareService) {
    this.publicShareService = publicShareService;
  }

  // ── GET /share/{token} ────────────────────────────────────────────────────

  @GetMapping(value = "/{token}", produces = MediaType.TEXT_HTML_VALUE)
  @Operation(
      summary = "View shared certificate page",
      description =
          "Public endpoint. Renders a branded HTML page with certificate details and document"
              + " download links. Returns 410 Gone if the token is expired or invalid.")
  public ResponseEntity<String> viewSharePage(@PathVariable final String token) {
    final SharePageResult result = publicShareService.resolveSharePage(token);
    if (result.expired()) {
      return ResponseEntity.status(HttpStatus.GONE)
          .contentType(MediaType.TEXT_HTML)
          .body(result.html());
    }
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(result.html());
  }

  // ── GET /share/{token}/download/{docId} ───────────────────────────────────

  @GetMapping("/{token}/download/{docId}")
  @Operation(
      summary = "Download a specific shared certificate document",
      description =
          "Public endpoint. Streams a specific document attached to the shared certificate."
              + " The docId must belong to the certificate linked to the token."
              + " Returns 410 Gone if the token is expired or invalid.")
  public ResponseEntity<StreamingResponseBody> downloadDocument(
      @PathVariable final String token, @PathVariable final UUID docId) {

    final Optional<DocumentMeta> metaOpt = publicShareService.resolveDocument(token, docId);
    if (metaOpt.isEmpty()) {
      return ResponseEntity.status(HttpStatus.GONE).build();
    }

    final DocumentMeta meta = metaOpt.get();
    final HttpHeaders headers = buildDownloadHeaders(meta.mimeType(), meta.filename());

    final StreamingResponseBody body =
        outputStream -> {
          try (InputStream in = publicShareService.openDocumentStream(meta.storageUrl())) {
            if (in == null) {
              log.warn("Document stream not found in storage: docId={}", docId);
              return;
            }
            in.transferTo(outputStream);
            log.info(
                "Shared document streamed: token={} docId={} filename={}",
                token,
                docId,
                meta.filename());
          }
        };

    return new ResponseEntity<>(body, headers, HttpStatus.OK);
  }

  // ── GET /share/{token}/download ───────────────────────────────────────────

  @GetMapping("/{token}/download")
  @Operation(
      summary = "Download all shared certificate documents",
      description =
          "Public endpoint. Streams a single file directly, or pipes multiple documents into"
              + " a zip archive without buffering into heap memory."
              + " Returns 410 Gone if the token is expired or invalid.")
  public ResponseEntity<StreamingResponseBody> downloadAll(@PathVariable final String token) {

    final Optional<List<DocumentMetaWithLabel>> metaOpt =
        publicShareService.resolveAllDocuments(token);
    if (metaOpt.isEmpty()) {
      return ResponseEntity.status(HttpStatus.GONE).build();
    }

    final List<DocumentMetaWithLabel> docs = metaOpt.get();

    if (docs.size() == 1) {
      // Single document — stream directly, no zip needed.
      final DocumentMetaWithLabel doc = docs.get(0);
      final HttpHeaders headers = buildDownloadHeaders(doc.mimeType(), doc.filename());

      final StreamingResponseBody body =
          outputStream -> {
            try (InputStream in = publicShareService.openDocumentStream(doc.storageUrl())) {
              if (in == null) {
                log.warn(
                    "Document stream not found for single-doc download: token={} url={}",
                    token,
                    doc.storageUrl());
                return;
              }
              in.transferTo(outputStream);
            }
          };

      return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    // Multiple documents — pipe each into a ZipOutputStream as it arrives, so no full zip
    // accumulates in heap. The Content-Length cannot be known upfront, which is fine for zip.
    final String zipName = docs.get(0).certTypeLabel().replace(" ", "_") + "_documents.zip";
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/zip"));
    headers.setContentDisposition(ContentDisposition.attachment().filename(zipName).build());

    final StreamingResponseBody body =
        outputStream -> {
          try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            for (final DocumentMetaWithLabel doc : docs) {
              try (InputStream in = publicShareService.openDocumentStream(doc.storageUrl())) {
                if (in == null) {
                  log.warn(
                      "Skipping missing document in zip: token={} url={}", token, doc.storageUrl());
                  continue;
                }
                zip.putNextEntry(new ZipEntry(doc.filename()));
                in.transferTo(zip);
                zip.closeEntry();
              }
            }
          }
          log.info("Shared zip streamed: token={} docs={}", token, docs.size());
        };

    return new ResponseEntity<>(body, headers, HttpStatus.OK);
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private static HttpHeaders buildDownloadHeaders(final String mimeType, final String filename) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(mimeType));
    headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
    return headers;
  }
}
