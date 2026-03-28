package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.ShareProperties;
import com.uk.certifynow.certify_now.domain.Certificate;
import com.uk.certifynow.certify_now.domain.CertificateDocument;
import com.uk.certifynow.certify_now.domain.Document;
import com.uk.certifynow.certify_now.domain.ShareToken;
import com.uk.certifynow.certify_now.repos.ShareTokenRepository;
import com.uk.certifynow.certify_now.service.ShareRenderService;
import com.uk.certifynow.certify_now.service.ShareRenderService.SharePageModel;
import com.uk.certifynow.certify_now.service.ShareRenderService.SharePageModel.DocumentLink;
import com.uk.certifynow.certify_now.service.storage.DocumentStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/share")
@Tag(name = "Public Share", description = "Public certificate share page endpoints")
public class PublicShareController {

  private final ShareTokenRepository shareTokenRepository;
  private final ShareRenderService shareRenderService;
  private final DocumentStorageService documentStorageService;
  private final ShareProperties shareProperties;
  private final Clock clock;

  public PublicShareController(
      final ShareTokenRepository shareTokenRepository,
      final ShareRenderService shareRenderService,
      final DocumentStorageService documentStorageService,
      final ShareProperties shareProperties,
      final Clock clock) {
    this.shareTokenRepository = shareTokenRepository;
    this.shareRenderService = shareRenderService;
    this.documentStorageService = documentStorageService;
    this.shareProperties = shareProperties;
    this.clock = clock;
  }

  // ── GET /share/{token} ────────────────────────────────────────────────────

  @GetMapping(value = "/{token}", produces = MediaType.TEXT_HTML_VALUE)
  @Operation(
      summary = "View shared certificate page",
      description =
          "Public endpoint. Renders a branded HTML page with certificate details and document"
              + " download links. Returns 410 Gone if the token is expired or invalid.")
  @Transactional
  public ResponseEntity<String> viewSharePage(@PathVariable final String token) {
    final Optional<ShareToken> shareTokenOpt = shareTokenRepository.findByToken(token);

    if (shareTokenOpt.isEmpty() || shareTokenOpt.get().isExpired(clock)) {
      shareTokenOpt.ifPresent(t -> log.info("Expired share token accessed: token={}", token));
      final String errorHtml = shareRenderService.renderErrorPage();
      return ResponseEntity.status(HttpStatus.GONE)
          .contentType(MediaType.TEXT_HTML)
          .body(errorHtml);
    }

    final ShareToken shareToken = shareTokenOpt.get();

    // Update access tracking
    shareToken.setAccessedAt(OffsetDateTime.now(clock));
    shareToken.setAccessCount(shareToken.getAccessCount() + 1);
    shareTokenRepository.save(shareToken);

    log.info(
        "Share page accessed: token={} certId={} accessCount={}",
        token,
        shareToken.getCertificate().getId(),
        shareToken.getAccessCount());

    final Certificate cert = shareToken.getCertificate();
    final String html = shareRenderService.renderSharePage(buildSharePageModel(cert, shareToken));
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
  }

  // ── GET /share/{token}/download/{docId} ───────────────────────────────────

  @GetMapping("/{token}/download/{docId}")
  @Operation(
      summary = "Download a specific shared certificate document",
      description =
          "Public endpoint. Streams a specific document attached to the shared certificate."
              + " The docId must belong to the certificate linked to the token."
              + " Returns 410 Gone if the token is expired or invalid.")
  @Transactional
  public ResponseEntity<byte[]> downloadDocument(
      @PathVariable final String token, @PathVariable final UUID docId) {

    final Optional<ShareToken> shareTokenOpt = shareTokenRepository.findByToken(token);

    if (shareTokenOpt.isEmpty() || shareTokenOpt.get().isExpired(clock)) {
      return ResponseEntity.status(HttpStatus.GONE).build();
    }

    final Certificate cert = shareTokenOpt.get().getCertificate();

    final Optional<Document> documentOpt =
        cert.getDocuments().stream()
            .filter(cd -> cd.getDocument() != null && docId.equals(cd.getDocument().getId()))
            .map(CertificateDocument::getDocument)
            .findFirst();

    if (documentOpt.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    final Document doc = documentOpt.get();
    final byte[] bytes = documentStorageService.retrieveByUrl(doc.getStorageUrl());

    if (bytes == null) {
      log.warn("Document bytes not found in storage: docId={}", docId);
      return ResponseEntity.notFound().build();
    }

    final String mimeType =
        doc.getMimeType() != null ? doc.getMimeType() : "application/octet-stream";
    final String filename =
        decodeFilename(doc.getFileName() != null ? doc.getFileName() : "certificate");

    log.info("Shared document downloaded: token={} docId={} filename={}", token, docId, filename);

    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(mimeType));
    headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
    headers.setContentLength(bytes.length);

    return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
  }

  // ── GET /share/{token}/download ───────────────────────────────────────────

  @GetMapping("/{token}/download")
  @Operation(
      summary = "Download all shared certificate documents",
      description =
          "Public endpoint. Downloads a single file directly, or bundles multiple documents into"
              + " a zip archive. Returns 410 Gone if the token is expired or invalid.")
  @Transactional
  public ResponseEntity<byte[]> downloadAll(@PathVariable final String token) {

    final Optional<ShareToken> shareTokenOpt = shareTokenRepository.findByToken(token);
    if (shareTokenOpt.isEmpty() || shareTokenOpt.get().isExpired(clock)) {
      return ResponseEntity.status(HttpStatus.GONE).build();
    }

    final Certificate cert = shareTokenOpt.get().getCertificate();

    final List<CertificateDocument> docs =
        cert.getDocuments().stream()
            .filter(cd -> cd.getDocument() != null && cd.getDocument().getStorageUrl() != null)
            .sorted(Comparator.comparingInt(CertificateDocument::getDisplayOrder))
            .toList();

    if (docs.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    // Single document — serve directly without a zip
    if (docs.size() == 1) {
      final Document doc = docs.get(0).getDocument();
      final byte[] bytes = documentStorageService.retrieveByUrl(doc.getStorageUrl());
      if (bytes == null) return ResponseEntity.notFound().build();
      final String mimeType =
          doc.getMimeType() != null ? doc.getMimeType() : "application/octet-stream";
      final String filename =
          decodeFilename(doc.getFileName() != null ? doc.getFileName() : "certificate");
      final HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.parseMediaType(mimeType));
      headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
      headers.setContentLength(bytes.length);
      return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    // Multiple documents — zip them
    try {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (final ZipOutputStream zip = new ZipOutputStream(baos)) {
        for (final CertificateDocument cd : docs) {
          final Document doc = cd.getDocument();
          final byte[] bytes = documentStorageService.retrieveByUrl(doc.getStorageUrl());
          if (bytes == null) continue;
          final String entryName =
              decodeFilename(doc.getFileName() != null ? doc.getFileName() : "document");
          zip.putNextEntry(new ZipEntry(entryName));
          zip.write(bytes);
          zip.closeEntry();
        }
      }
      final byte[] zipBytes = baos.toByteArray();
      final String zipName =
          friendlyCertType(cert.getCertificateType()).replace(" ", "_") + "_documents.zip";
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

  // ── Helpers ───────────────────────────────────────────────────────────────

  private SharePageModel buildSharePageModel(final Certificate cert, final ShareToken shareToken) {

    final String propertyAddress = buildPropertyAddress(cert);
    final String certTypeName = friendlyCertType(cert.getCertificateType());
    final String status = calculateDisplayStatus(cert, clock);
    final String engineerName =
        cert.getIssuedByEngineer() != null ? cert.getIssuedByEngineer().getFullName() : null;

    final String baseToken = shareProperties.getBaseUrl() + "/share/" + shareToken.getToken();

    final List<DocumentLink> documents =
        cert.getDocuments().stream()
            .filter(cd -> cd.getDocument() != null && cd.getDocument().getStorageUrl() != null)
            .sorted(Comparator.comparingInt(CertificateDocument::getDisplayOrder))
            .map(
                cd -> {
                  final Document doc = cd.getDocument();
                  final String downloadUrl = baseToken + "/download/" + doc.getId();
                  final String fileName =
                      decodeFilename(doc.getFileName() != null ? doc.getFileName() : "certificate");
                  final String fileSize = formatFileSize(doc.getFileSizeBytes());
                  return new DocumentLink(fileName, doc.getMimeType(), downloadUrl, fileSize);
                })
            .toList();

    final String downloadAllUrl = documents.isEmpty() ? null : baseToken + "/download";

    return new SharePageModel(
        certTypeName,
        propertyAddress,
        cert.getIssuedAt(),
        cert.getExpiryAt(),
        status,
        engineerName,
        shareToken.getExpiresAt(),
        documents,
        downloadAllUrl);
  }

  private static String decodeFilename(final String filename) {
    try {
      return URLDecoder.decode(filename, StandardCharsets.UTF_8);
    } catch (final Exception e) {
      return filename;
    }
  }

  private static String formatFileSize(final Long bytes) {
    if (bytes == null || bytes <= 0) return "";
    if (bytes < 1024L * 1024L) return Math.round(bytes / 1024.0) + " KB";
    return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
  }

  private static String buildPropertyAddress(final Certificate cert) {
    if (cert.getProperty() == null) return "Unknown property";
    final var p = cert.getProperty();
    final StringBuilder sb = new StringBuilder();
    if (p.getAddressLine1() != null) sb.append(p.getAddressLine1());
    if (p.getAddressLine2() != null && !p.getAddressLine2().isBlank()) {
      sb.append(", ").append(p.getAddressLine2());
    }
    if (p.getCity() != null) sb.append(", ").append(p.getCity());
    if (p.getPostcode() != null) sb.append(", ").append(p.getPostcode());
    return sb.toString();
  }

  private static String friendlyCertType(final String type) {
    if (type == null) return "Certificate";
    return switch (type) {
      case "GAS_SAFETY" -> "Gas Safety";
      case "EICR" -> "EICR";
      case "EPC" -> "EPC";
      case "PAT" -> "PAT Testing";
      case "FIRE_RISK_ASSESSMENT" -> "Fire Risk Assessment";
      case "BOILER_SERVICE" -> "Boiler Service";
      case "LEGIONELLA_RISK_ASSESSMENT" -> "Legionella Assessment";
      default -> type.replace("_", " ");
    };
  }

  private static String calculateDisplayStatus(final Certificate cert, final Clock clock) {
    if (cert.getExpiryAt() == null) return "VALID";
    final java.time.LocalDate today = java.time.LocalDate.now(clock);
    if (cert.getExpiryAt().isBefore(today)) return "EXPIRED";
    if (cert.getExpiryAt().isBefore(today.plusDays(90))) return "EXPIRING_SOON";
    return "VALID";
  }
}
