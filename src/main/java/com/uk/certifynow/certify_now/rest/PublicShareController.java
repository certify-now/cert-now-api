package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.service.PublicShareService;
import com.uk.certifynow.certify_now.service.PublicShareService.DocumentResult;
import com.uk.certifynow.certify_now.service.PublicShareService.DocumentWithMetadata;
import com.uk.certifynow.certify_now.service.PublicShareService.SharePageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
  public ResponseEntity<byte[]> downloadDocument(
      @PathVariable final String token, @PathVariable final UUID docId) {
    final Optional<DocumentResult> result = publicShareService.resolveDocument(token, docId);
    if (result.isEmpty()) {
      return ResponseEntity.status(HttpStatus.GONE).build();
    }
    final DocumentResult doc = result.get();
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(doc.mimeType()));
    headers.setContentDisposition(ContentDisposition.attachment().filename(doc.filename()).build());
    headers.setContentLength(doc.bytes().length);
    return new ResponseEntity<>(doc.bytes(), headers, HttpStatus.OK);
  }

  // ── GET /share/{token}/download ───────────────────────────────────────────

  @GetMapping("/{token}/download")
  @Operation(
      summary = "Download all shared certificate documents",
      description =
          "Public endpoint. Downloads a single file directly, or bundles multiple documents into"
              + " a zip archive. Returns 410 Gone if the token is expired or invalid.")
  public ResponseEntity<byte[]> downloadAll(@PathVariable final String token) {
    final Optional<List<DocumentWithMetadata>> result =
        publicShareService.resolveAllDocuments(token);
    if (result.isEmpty()) {
      return ResponseEntity.status(HttpStatus.GONE).build();
    }

    final List<DocumentWithMetadata> docs = result.get();

    if (docs.size() == 1) {
      final DocumentWithMetadata doc = docs.get(0);
      if (doc.bytes() == null) return ResponseEntity.notFound().build();
      final HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.parseMediaType(doc.mimeType()));
      headers.setContentDisposition(
          ContentDisposition.attachment().filename(doc.filename()).build());
      headers.setContentLength(doc.bytes().length);
      return new ResponseEntity<>(doc.bytes(), headers, HttpStatus.OK);
    }

    try {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (final ZipOutputStream zip = new ZipOutputStream(baos)) {
        for (final DocumentWithMetadata doc : docs) {
          if (doc.bytes() == null) continue;
          zip.putNextEntry(new ZipEntry(doc.filename()));
          zip.write(doc.bytes());
          zip.closeEntry();
        }
      }
      final byte[] zipBytes = baos.toByteArray();
      final String zipName = docs.get(0).certTypeLabel().replace(" ", "_") + "_documents.zip";
      final HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.parseMediaType("application/zip"));
      headers.setContentDisposition(ContentDisposition.attachment().filename(zipName).build());
      headers.setContentLength(zipBytes.length);
      log.info("Shared zip downloaded: token={} docs={}", token, docs.size());
      return new ResponseEntity<>(zipBytes, headers, HttpStatus.OK);
    } catch (final IOException e) {
      log.error("Failed to build zip for token={}", token, e);
      return ResponseEntity.internalServerError().build();
    }
  }
}
