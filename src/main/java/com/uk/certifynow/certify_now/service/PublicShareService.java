package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.config.ShareProperties;
import com.uk.certifynow.certify_now.domain.Certificate;
import com.uk.certifynow.certify_now.domain.CertificateDocument;
import com.uk.certifynow.certify_now.domain.Document;
import com.uk.certifynow.certify_now.domain.ShareToken;
import com.uk.certifynow.certify_now.repos.ShareTokenRepository;
import com.uk.certifynow.certify_now.service.ShareRenderService.SharePageModel;
import com.uk.certifynow.certify_now.service.ShareRenderService.SharePageModel.DocumentLink;
import com.uk.certifynow.certify_now.service.storage.DocumentStorageService;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PublicShareService {

  private final ShareTokenRepository shareTokenRepository;
  private final ShareRenderService shareRenderService;
  private final DocumentStorageService documentStorageService;
  private final ShareProperties shareProperties;
  private final Clock clock;

  public PublicShareService(
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

  /** Result returned when a valid share token is resolved for the share page. */
  public record SharePageResult(boolean expired, String html) {}

  /** Result returned when a document is resolved for download. */
  public record DocumentResult(byte[] bytes, String mimeType, String filename) {}

  /**
   * Validates the share token, records an access hit, and renders the HTML page. Returns a result
   * indicating expiry so the controller can choose the correct HTTP status.
   */
  @Transactional
  public SharePageResult resolveSharePage(final String token) {
    final Optional<ShareToken> shareTokenOpt = shareTokenRepository.findByToken(token);

    if (shareTokenOpt.isEmpty() || shareTokenOpt.get().isExpired(clock)) {
      shareTokenOpt.ifPresent(t -> log.info("Expired share token accessed: token={}", token));
      return new SharePageResult(true, shareRenderService.renderErrorPage());
    }

    final ShareToken shareToken = shareTokenOpt.get();
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
    return new SharePageResult(false, html);
  }

  /**
   * Validates the share token and returns the bytes and metadata for a single document. Returns
   * empty if the token is expired or the document does not belong to the certificate.
   */
  @Transactional(readOnly = true)
  public Optional<DocumentResult> resolveDocument(final String token, final UUID docId) {
    final Optional<ShareToken> shareTokenOpt = shareTokenRepository.findByToken(token);
    if (shareTokenOpt.isEmpty() || shareTokenOpt.get().isExpired(clock)) {
      return Optional.empty();
    }

    final Certificate cert = shareTokenOpt.get().getCertificate();
    final Optional<Document> documentOpt =
        cert.getDocuments().stream()
            .filter(cd -> cd.getDocument() != null && docId.equals(cd.getDocument().getId()))
            .map(CertificateDocument::getDocument)
            .findFirst();

    if (documentOpt.isEmpty()) {
      return Optional.empty();
    }

    final Document doc = documentOpt.get();
    final byte[] bytes = documentStorageService.retrieveByUrl(doc.getStorageUrl());
    if (bytes == null) {
      log.warn("Document bytes not found in storage: docId={}", docId);
      return Optional.empty();
    }

    final String mimeType =
        doc.getMimeType() != null ? doc.getMimeType() : "application/octet-stream";
    final String filename =
        decodeFilename(doc.getFileName() != null ? doc.getFileName() : "certificate");
    log.info("Shared document downloaded: token={} docId={} filename={}", token, docId, filename);
    return Optional.of(new DocumentResult(bytes, mimeType, filename));
  }

  /**
   * Validates the share token and returns the documents for a bulk download. Returns empty if the
   * token is expired or there are no documents attached.
   */
  @Transactional(readOnly = true)
  public Optional<List<DocumentWithMetadata>> resolveAllDocuments(final String token) {
    final Optional<ShareToken> shareTokenOpt = shareTokenRepository.findByToken(token);
    if (shareTokenOpt.isEmpty() || shareTokenOpt.get().isExpired(clock)) {
      return Optional.empty();
    }

    final Certificate cert = shareTokenOpt.get().getCertificate();
    final List<CertificateDocument> docs =
        cert.getDocuments().stream()
            .filter(cd -> cd.getDocument() != null && cd.getDocument().getStorageUrl() != null)
            .sorted(Comparator.comparingInt(CertificateDocument::getDisplayOrder))
            .toList();

    if (docs.isEmpty()) {
      return Optional.empty();
    }

    final String certTypeLabel = friendlyCertType(cert.getCertificateType());
    final List<DocumentWithMetadata> results =
        docs.stream()
            .map(
                cd -> {
                  final Document doc = cd.getDocument();
                  final byte[] bytes = documentStorageService.retrieveByUrl(doc.getStorageUrl());
                  final String mimeType =
                      doc.getMimeType() != null ? doc.getMimeType() : "application/octet-stream";
                  final String filename =
                      decodeFilename(doc.getFileName() != null ? doc.getFileName() : "certificate");
                  return new DocumentWithMetadata(bytes, mimeType, filename, certTypeLabel);
                })
            .toList();

    return Optional.of(results);
  }

  /** Carries bytes + metadata for a document in a multi-document download response. */
  public record DocumentWithMetadata(
      byte[] bytes, String mimeType, String filename, String certTypeLabel) {}

  // ── Private helpers ──────────────────────────────────────────────────────

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
