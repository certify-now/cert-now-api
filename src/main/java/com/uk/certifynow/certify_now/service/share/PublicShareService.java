package com.uk.certifynow.certify_now.service.share;

import com.uk.certifynow.certify_now.config.ShareProperties;
import com.uk.certifynow.certify_now.domain.Certificate;
import com.uk.certifynow.certify_now.domain.CertificateDocument;
import com.uk.certifynow.certify_now.domain.Document;
import com.uk.certifynow.certify_now.domain.ShareToken;
import com.uk.certifynow.certify_now.domain.enums.CertificateStatus;
import com.uk.certifynow.certify_now.domain.enums.CertificateType;
import com.uk.certifynow.certify_now.model.NotificationPrefsDTO;
import com.uk.certifynow.certify_now.repos.ShareTokenRepository;
import com.uk.certifynow.certify_now.service.ShareRenderService.SharePageModel;
import com.uk.certifynow.certify_now.service.ShareRenderService.SharePageModel.DocumentLink;
import com.uk.certifynow.certify_now.service.storage.DocumentStorageService;
import java.io.InputStream;
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
  private final UserService userService;
  private final Clock clock;

  public PublicShareService(
      final ShareTokenRepository shareTokenRepository,
      final ShareRenderService shareRenderService,
      final DocumentStorageService documentStorageService,
      final ShareProperties shareProperties,
      final UserService userService,
      final Clock clock) {
    this.shareTokenRepository = shareTokenRepository;
    this.shareRenderService = shareRenderService;
    this.documentStorageService = documentStorageService;
    this.shareProperties = shareProperties;
    this.userService = userService;
    this.clock = clock;
  }

  /** Result returned when a valid share token is resolved for the share page. */
  public record SharePageResult(boolean expired, String html) {}

  /**
   * Metadata returned when a document is resolved for download. No bytes are loaded here — the
   * controller opens the stream directly via {@link #openDocumentStream}.
   */
  public record DocumentMeta(String storageUrl, String mimeType, String filename) {}

  /**
   * Metadata for one document in a bulk download. No bytes are loaded here — the controller opens
   * each stream directly via {@link #openDocumentStream}.
   */
  public record DocumentMetaWithLabel(
      String storageUrl, String mimeType, String filename, String certTypeLabel) {}

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
    final int expiringSoonDays = resolveOwnerExpiringSoonDays(cert);
    final String html =
        shareRenderService.renderSharePage(buildSharePageModel(cert, shareToken, expiringSoonDays));
    return new SharePageResult(false, html);
  }

  /**
   * Validates the share token and returns metadata for a single document. Returns empty if the
   * token is expired or the document does not belong to the certificate. No bytes are loaded.
   */
  @Transactional(readOnly = true)
  public Optional<DocumentMeta> resolveDocument(final String token, final UUID docId) {
    final Optional<ShareToken> shareTokenOpt = shareTokenRepository.findByToken(token);
    if (shareTokenOpt.isEmpty() || shareTokenOpt.get().isExpired(clock)) {
      return Optional.empty();
    }

    final Certificate cert = shareTokenOpt.get().getCertificate();
    return cert.getDocuments().stream()
        .filter(cd -> cd.getDocument() != null && docId.equals(cd.getDocument().getId()))
        .map(CertificateDocument::getDocument)
        .findFirst()
        .map(
            doc -> {
              final String mimeType =
                  doc.getMimeType() != null ? doc.getMimeType() : "application/octet-stream";
              final String filename =
                  decodeFilename(doc.getFileName() != null ? doc.getFileName() : "certificate");
              return new DocumentMeta(doc.getStorageUrl(), mimeType, filename);
            });
  }

  /**
   * Validates the share token and returns metadata for all documents on the certificate. Returns
   * empty if the token is expired or there are no documents. No bytes are loaded.
   */
  @Transactional(readOnly = true)
  public Optional<List<DocumentMetaWithLabel>> resolveAllDocuments(final String token) {
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
    final List<DocumentMetaWithLabel> results =
        docs.stream()
            .map(
                cd -> {
                  final Document doc = cd.getDocument();
                  final String mimeType =
                      doc.getMimeType() != null ? doc.getMimeType() : "application/octet-stream";
                  final String filename =
                      decodeFilename(doc.getFileName() != null ? doc.getFileName() : "certificate");
                  return new DocumentMetaWithLabel(
                      doc.getStorageUrl(), mimeType, filename, certTypeLabel);
                })
            .toList();

    return Optional.of(results);
  }

  /**
   * Opens a streaming {@link InputStream} for the given storage URL. The caller is responsible for
   * closing the stream (use try-with-resources). Returns {@code null} if the object is not found.
   */
  public InputStream openDocumentStream(final String storageUrl) {
    return documentStorageService.streamByUrl(storageUrl);
  }

  // ── Private helpers ──────────────────────────────────────────────────────

  private SharePageModel buildSharePageModel(
      final Certificate cert, final ShareToken shareToken, final int expiringSoonDays) {
    final String propertyAddress = buildPropertyAddress(cert);
    final String certTypeName = friendlyCertType(cert.getCertificateType());
    final String status = calculateDisplayStatus(cert, clock, expiringSoonDays).name();
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
    try {
      return CertificateType.valueOf(type).getDisplayName();
    } catch (final IllegalArgumentException e) {
      return type.replace("_", " ");
    }
  }

  private static CertificateStatus calculateDisplayStatus(
      final Certificate cert, final Clock clock, final int expiringSoonDays) {
    if (cert.getExpiryAt() == null) return CertificateStatus.VALID;
    final java.time.LocalDate today = java.time.LocalDate.now(clock);
    if (cert.getExpiryAt().isBefore(today)) return CertificateStatus.EXPIRED;
    if (cert.getExpiryAt().isBefore(today.plusDays(expiringSoonDays)))
      return CertificateStatus.EXPIRING_SOON;
    return CertificateStatus.VALID;
  }

  private int resolveOwnerExpiringSoonDays(final Certificate cert) {
    try {
      if (cert.getProperty() != null && cert.getProperty().getOwner() != null) {
        return userService.resolveExpiringSoonDays(cert.getProperty().getOwner().getId());
      }
    } catch (Exception e) {
      // fall through to default
    }
    return NotificationPrefsDTO.DEFAULT_EXPIRING_SOON_DAYS;
  }
}
