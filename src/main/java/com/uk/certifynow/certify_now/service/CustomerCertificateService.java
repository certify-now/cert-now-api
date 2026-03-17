package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Certificate;
import com.uk.certifynow.certify_now.domain.EicrInspection;
import com.uk.certifynow.certify_now.domain.GasSafetyRecord;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.repos.CertificateRepository;
import com.uk.certifynow.certify_now.repos.EicrInspectionRepository;
import com.uk.certifynow.certify_now.repos.GasSafetyRecordRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.rest.dto.certificate.CertificateDetailResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.CertificateDetailResponse.ApplianceSummary;
import com.uk.certifynow.certify_now.rest.dto.certificate.CertificateDetailResponse.EicrInspectionSummary;
import com.uk.certifynow.certify_now.rest.dto.certificate.CertificateDetailResponse.EpcAssessmentSummary;
import com.uk.certifynow.certify_now.rest.dto.certificate.CertificateDetailResponse.GasInspectionSummary;
import com.uk.certifynow.certify_now.rest.dto.certificate.CertificateDownloadResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.CertificateListItemResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.CertificatesListResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.CertificatesListResponse.Breakdown;
import com.uk.certifynow.certify_now.rest.dto.certificate.CertificatesListResponse.Meta;
import com.uk.certifynow.certify_now.rest.dto.certificate.EngineerSummaryResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.GetCertificatesRequest;
import com.uk.certifynow.certify_now.rest.dto.certificate.MissingCertificateResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.PropertySummaryResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.ShareCertificateResponse;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import com.uk.certifynow.certify_now.service.storage.DocumentStorageService;
import com.uk.certifynow.certify_now.util.NotFoundException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class CustomerCertificateService {

  private static final int EXPIRING_SOON_DAYS = 60;
  private static final int EICR_MAX_AGE_YEARS = 5;
  private static final int EPC_MAX_AGE_YEARS = 10;
  private static final int PAT_MAX_AGE_YEARS = 1;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final CertificateRepository certificateRepository;
  private final PropertyRepository propertyRepository;
  private final GasSafetyRecordRepository gasSafetyRecordRepository;
  private final EicrInspectionRepository eicrInspectionRepository;
  private final DocumentStorageService documentStorageService;
  private final Clock clock;

  public CustomerCertificateService(
      final CertificateRepository certificateRepository,
      final PropertyRepository propertyRepository,
      final GasSafetyRecordRepository gasSafetyRecordRepository,
      final EicrInspectionRepository eicrInspectionRepository,
      final DocumentStorageService documentStorageService,
      final Clock clock) {
    this.certificateRepository = certificateRepository;
    this.propertyRepository = propertyRepository;
    this.gasSafetyRecordRepository = gasSafetyRecordRepository;
    this.eicrInspectionRepository = eicrInspectionRepository;
    this.documentStorageService = documentStorageService;
    this.clock = clock;
  }

  // ── GET /my-certificates ─────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public CertificatesListResponse getCustomerCertificates(
      final UUID customerId, final GetCertificatesRequest filters) {

    final LocalDate today = LocalDate.now(clock);
    final List<Certificate> rawCerts =
        certificateRepository.findByPropertyOwnerIdWithFilters(
            customerId, filters.type(), filters.propertyId());

    // Compute dynamic status and build list items
    final List<CertificateListItemResponse> items = new ArrayList<>();
    for (final Certificate cert : rawCerts) {
      final String dynamicStatus = calculateStatus(cert.getExpiryAt(), cert.getStatus(), today);

      // Apply status filter after dynamic calculation
      if (filters.status() != null && !filters.status().equalsIgnoreCase(dynamicStatus)) {
        continue;
      }

      items.add(toListItem(cert, dynamicStatus, today, customerId));
    }

    // Append MISSING entries (only when no status filter, or when filter is MISSING)
    if (filters.status() == null || "MISSING".equalsIgnoreCase(filters.status())) {
      final List<Property> properties =
          propertyRepository.findByOwnerIdAndIsActiveTrue(customerId, Sort.by("addressLine1"));
      final Map<String, List<Certificate>> activeCertsByKey =
          batchLoadActiveCerts(properties.stream().map(Property::getId).toList());
      for (final Property property : properties) {
        if (filters.propertyId() != null && !filters.propertyId().equals(property.getId())) {
          continue;
        }
        final List<MissingEntry> missing =
            detectMissingForProperty(property, activeCertsByKey, today);
        for (final MissingEntry entry : missing) {
          if (filters.type() != null && !filters.type().equalsIgnoreCase(entry.certificateType())) {
            continue;
          }
          items.add(toMissingListItem(property, entry.certificateType()));
        }
      }
    }

    // Apply smart sort (or requested sort)
    sortItems(items, filters.sort());

    // Build meta breakdown
    final Meta meta = buildMeta(items);

    return new CertificatesListResponse(items, meta);
  }

  // ── GET /{id} ─────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public CertificateDetailResponse getCertificateForUser(
      final UUID certId, final UUID requestingUserId, final UserRole role) {

    final Certificate cert =
        certificateRepository
            .findByIdWithDetails(certId)
            .orElseThrow(() -> new NotFoundException("Certificate not found"));

    verifyAccess(cert, requestingUserId, role);

    final LocalDate today = LocalDate.now(clock);
    final String dynamicStatus = calculateStatus(cert.getExpiryAt(), cert.getStatus(), today);

    // Load inspection data
    GasInspectionSummary gasInspection = null;
    EicrInspectionSummary eicrInspection = null;
    EpcAssessmentSummary epcAssessment = null;

    if (cert.getJob() != null) {
      final UUID jobId = cert.getJob().getId();
      switch (cert.getCertificateType()) {
        case "GAS_SAFETY" -> {
          final Optional<GasSafetyRecord> record =
              gasSafetyRecordRepository.findByJobIdWithAppliances(jobId);
          if (record.isPresent()) {
            final GasSafetyRecord r = record.get();
            final List<ApplianceSummary> appliances =
                r.getAppliances().stream()
                    .map(
                        a ->
                            new ApplianceSummary(
                                a.getApplianceType(),
                                a.getLocation(),
                                a.getMake(),
                                a.getModel(),
                                Boolean.TRUE.equals(a.getApplianceSafeToUse()) ? "SAFE" : "UNSAFE"))
                    .toList();
            gasInspection = new GasInspectionSummary(appliances.size(), appliances);
          }
        }
        case "EICR" -> {
          final EicrInspection inspection = eicrInspectionRepository.findFirstByJobId(jobId);
          if (inspection != null) {
            eicrInspection =
                new EicrInspectionSummary(
                    inspection.getC1Count(),
                    inspection.getC2Count(),
                    inspection.getC3Count(),
                    inspection.getFiCount(),
                    inspection.getInspectionDate(),
                    inspection.getNextInspectionDate(),
                    inspection.getOverallResult());
          }
        }
        case "EPC" -> {
          // EPC data is minimal — rating/score already on the certificate
          epcAssessment =
              new EpcAssessmentSummary(cert.getEpcRating(), cert.getEpcScore(), null, null);
        }
        default -> {
          // PAT or unknown — no inspection summary
        }
      }
    } else if ("EPC".equals(cert.getCertificateType())) {
      epcAssessment = new EpcAssessmentSummary(cert.getEpcRating(), cert.getEpcScore(), null, null);
    }

    final boolean canDownload = canDownload(cert, requestingUserId, role);
    final boolean canShare = canShare(cert, requestingUserId, role);
    final boolean canRenew = canRenew(cert, dynamicStatus);

    return new CertificateDetailResponse(
        cert.getId(),
        cert.getCertificateNumber(),
        cert.getCertificateType(),
        toPropertySummary(cert.getProperty()),
        dynamicStatus,
        cert.getResult(),
        cert.getIssuedAt(),
        cert.getExpiryAt(),
        daysUntilExpiry(cert.getExpiryAt(), today),
        cert.getDocumentUrl(),
        cert.getShareToken(),
        buildShareUrl(cert.getShareToken()),
        canDownload,
        canShare,
        canRenew,
        toEngineerSummary(cert),
        cert.getJob() != null ? cert.getJob().getId() : null,
        gasInspection,
        eicrInspection,
        epcAssessment);
  }

  // ── GET /{id}/download ────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public CertificateDownloadPair downloadCertificate(
      final UUID certId, final UUID requestingUserId, final UserRole role) {

    final Certificate cert =
        certificateRepository
            .findByIdWithProperty(certId)
            .orElseThrow(() -> new NotFoundException("Certificate not found"));

    verifyAccess(cert, requestingUserId, role);

    if ("EPC".equals(cert.getCertificateType())) {
      throw new IllegalArgumentException(
          "EPC certificates are not downloadable — use the government EPC register.");
    }

    final byte[] pdfBytes = documentStorageService.retrieve(cert.getId());
    if (pdfBytes == null) {
      throw new NotFoundException("PDF document not found for this certificate");
    }

    final String filename = buildFilename(cert);
    log.info(
        "Certificate PDF downloaded: certId={} userId={} filename={}",
        certId,
        requestingUserId,
        filename);

    return new CertificateDownloadPair(pdfBytes, new CertificateDownloadResponse(filename));
  }

  /** Pairs the raw bytes with filename metadata for the controller. */
  public record CertificateDownloadPair(byte[] bytes, CertificateDownloadResponse meta) {}

  // ── POST /{id}/share ──────────────────────────────────────────────────────

  @Transactional
  public ShareCertificateResponse shareCertificate(final UUID certId, final UUID requestingUserId) {

    final Certificate cert =
        certificateRepository
            .findByIdWithProperty(certId)
            .orElseThrow(() -> new NotFoundException("Certificate not found"));

    verifyOwnership(cert, requestingUserId);

    if (cert.getShareToken() == null) {
      final byte[] tokenBytes = new byte[32];
      SECURE_RANDOM.nextBytes(tokenBytes);
      final StringBuilder hex = new StringBuilder(64);
      for (final byte b : tokenBytes) {
        hex.append(String.format("%02x", b));
      }
      cert.setShareToken(hex.toString());
      cert.setShareTokenCreated(OffsetDateTime.now(clock));
      certificateRepository.save(cert);
      log.info("Share token created for certId={} by userId={}", certId, requestingUserId);
    }

    return new ShareCertificateResponse(
        "/api/v1/certificates/shared/" + cert.getShareToken(), cert.getShareTokenCreated());
  }

  // ── DELETE /{id}/share ────────────────────────────────────────────────────

  @Transactional
  public void revokeShare(final UUID certId, final UUID requestingUserId) {
    final Certificate cert =
        certificateRepository
            .findByIdWithProperty(certId)
            .orElseThrow(() -> new NotFoundException("Certificate not found"));

    verifyOwnership(cert, requestingUserId);

    cert.setShareToken(null);
    cert.setShareTokenCreated(null);
    certificateRepository.save(cert);
    log.info("Share token revoked for certId={} by userId={}", certId, requestingUserId);
  }

  // ── GET /missing ──────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<MissingCertificateResponse> getMissingCertificates(final UUID customerId) {
    final LocalDate today = LocalDate.now(clock);
    final List<Property> properties =
        propertyRepository.findByOwnerIdAndIsActiveTrue(customerId, Sort.by("addressLine1"));

    final Map<String, List<Certificate>> activeCertsByKey =
        batchLoadActiveCerts(properties.stream().map(Property::getId).toList());

    final List<MissingCertificateResponse> result = new ArrayList<>();
    for (final Property property : properties) {
      for (final MissingEntry entry : detectMissingForProperty(property, activeCertsByKey, today)) {
        result.add(
            MissingCertificateResponse.of(
                property.getId(),
                property.getAddressLine1(),
                property.getAddressLine2(),
                property.getCity(),
                property.getPostcode(),
                entry.certificateType(),
                entry.reason(),
                entry.urgency()));
      }
    }
    return result;
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private record MissingEntry(String certificateType, String reason, String urgency) {}

  /**
   * Batch-loads all ACTIVE certificates for the given property IDs in a single query and groups
   * them by {@code "propertyId|certificateType"} for O(1) lookup in {@link
   * #detectMissingForProperty}.
   */
  private Map<String, List<Certificate>> batchLoadActiveCerts(final List<UUID> propertyIds) {
    if (propertyIds.isEmpty()) return Map.of();
    final Map<String, List<Certificate>> map = new HashMap<>();
    for (final Certificate c : certificateRepository.findAllActiveCertsByPropertyIds(propertyIds)) {
      final String key = c.getProperty().getId() + "|" + c.getCertificateType();
      map.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
    }
    return map;
  }

  private List<MissingEntry> detectMissingForProperty(
      final Property property,
      final Map<String, List<Certificate>> activeCertsByKey,
      final LocalDate today) {
    final List<MissingEntry> entries = new ArrayList<>();
    final UUID pid = property.getId();

    // Gas Safety: required if property has gas supply
    if (Boolean.TRUE.equals(property.getHasGasSupply())) {
      final List<Certificate> allActive =
          activeCertsByKey.getOrDefault(pid + "|GAS_SAFETY", List.of());
      final List<Certificate> nonExpired =
          allActive.stream()
              .filter(c -> c.getExpiryAt() == null || !c.getExpiryAt().isBefore(today))
              .toList();
      if (nonExpired.isEmpty()) {
        entries.add(
            new MissingEntry(
                "GAS_SAFETY",
                !allActive.isEmpty()
                    ? "Gas safety certificate has expired"
                    : "No gas safety certificate on record",
                "HIGH"));
      }
      // Certs expiring soon already surface as EXPIRING_SOON in the main list;
      // no duplicate MISSING entry is needed here.
    }

    // EPC: required for all residential properties (not commercial/other)
    final String propertyType = property.getPropertyType();
    final boolean isResidential =
        propertyType != null
            && !propertyType.equalsIgnoreCase("COMMERCIAL")
            && !propertyType.equalsIgnoreCase("OTHER");
    if (isResidential) {
      final LocalDate epcCutoff = today.minusYears(EPC_MAX_AGE_YEARS);
      final List<Certificate> epcCerts =
          activeCertsByKey.getOrDefault(pid + "|EPC", List.of()).stream()
              .filter(c -> c.getExpiryAt() == null || !c.getExpiryAt().isBefore(today))
              .toList();
      final boolean hasValidEpc =
          epcCerts.stream()
              .anyMatch(c -> c.getIssuedAt() != null && c.getIssuedAt().isAfter(epcCutoff));
      if (!hasValidEpc) {
        entries.add(
            new MissingEntry(
                "EPC",
                epcCerts.isEmpty()
                    ? "No EPC certificate on record"
                    : "EPC certificate is more than 10 years old",
                "HIGH"));
      }
    }

    // EICR: required if property has electric supply (5-year cycle)
    if (Boolean.TRUE.equals(property.getHasElectric()) && isResidential) {
      final LocalDate eicrCutoff = today.minusYears(EICR_MAX_AGE_YEARS);
      final List<Certificate> eicrCerts =
          activeCertsByKey.getOrDefault(pid + "|EICR", List.of()).stream()
              .filter(c -> c.getExpiryAt() == null || !c.getExpiryAt().isBefore(today))
              .toList();
      final boolean hasValidEicr =
          eicrCerts.stream()
              .anyMatch(c -> c.getIssuedAt() != null && c.getIssuedAt().isAfter(eicrCutoff));
      if (!hasValidEicr) {
        entries.add(
            new MissingEntry(
                "EICR",
                eicrCerts.isEmpty()
                    ? "No EICR certificate on record"
                    : "EICR certificate is more than 5 years old",
                "HIGH"));
      }
    }

    // PAT: required for HMO or commercial properties
    if ("HMO".equalsIgnoreCase(propertyType) || "COMMERCIAL".equalsIgnoreCase(propertyType)) {
      final LocalDate patCutoff = today.minusYears(PAT_MAX_AGE_YEARS);
      final List<Certificate> patCerts =
          activeCertsByKey.getOrDefault(pid + "|PAT", List.of()).stream()
              .filter(c -> c.getExpiryAt() == null || !c.getExpiryAt().isBefore(today))
              .toList();
      final boolean hasValidPat =
          patCerts.stream()
              .anyMatch(c -> c.getIssuedAt() != null && c.getIssuedAt().isAfter(patCutoff));
      if (!hasValidPat) {
        entries.add(
            new MissingEntry(
                "PAT",
                patCerts.isEmpty()
                    ? "No PAT certificate on record"
                    : "PAT certificate is more than 1 year old",
                "HIGH"));
      }
    }

    return entries;
  }

  static String calculateStatus(
      final LocalDate expiryAt, final String entityStatus, final LocalDate today) {
    if ("SUPERSEDED".equals(entityStatus)) {
      return "SUPERSEDED";
    }
    if (expiryAt == null) {
      return "VALID";
    }
    final long days = ChronoUnit.DAYS.between(today, expiryAt);
    if (days < 0) return "EXPIRED";
    if (days <= EXPIRING_SOON_DAYS) return "EXPIRING_SOON";
    return "VALID";
  }

  private static Long daysUntilExpiry(final LocalDate expiryAt, final LocalDate today) {
    if (expiryAt == null) return null;
    return ChronoUnit.DAYS.between(today, expiryAt);
  }

  private boolean canDownload(
      final Certificate cert, final UUID requestingUserId, final UserRole role) {
    if ("EPC".equals(cert.getCertificateType())) return false;
    if (cert.getDocumentUrl() == null) return false;
    return isOwner(cert, requestingUserId)
        || role.isAdmin()
        || isIssuingEngineer(cert, requestingUserId);
  }

  private boolean canShare(
      final Certificate cert, final UUID requestingUserId, final UserRole role) {
    // Only the property owner may create/use share links; shareCertificate enforces
    // the same constraint via verifyOwnership, so admin is intentionally excluded here.
    return isOwner(cert, requestingUserId);
  }

  private boolean canRenew(final Certificate cert, final String dynamicStatus) {
    return "EXPIRED".equals(dynamicStatus) || "EXPIRING_SOON".equals(dynamicStatus);
  }

  private boolean isOwner(final Certificate cert, final UUID userId) {
    return cert.getProperty() != null
        && cert.getProperty().getOwner() != null
        && userId.equals(cert.getProperty().getOwner().getId());
  }

  private boolean isIssuingEngineer(final Certificate cert, final UUID userId) {
    return cert.getIssuedByEngineer() != null && userId.equals(cert.getIssuedByEngineer().getId());
  }

  private void verifyAccess(
      final Certificate cert, final UUID requestingUserId, final UserRole role) {
    if (role.isAdmin()) return;
    if (isOwner(cert, requestingUserId)) return;
    if (role.isEngineer() && isIssuingEngineer(cert, requestingUserId)) return;
    throw new NotFoundException("Certificate not found");
  }

  private void verifyOwnership(final Certificate cert, final UUID requestingUserId) {
    if (!isOwner(cert, requestingUserId)) {
      throw new NotFoundException("Certificate not found");
    }
  }

  private CertificateListItemResponse toListItem(
      final Certificate cert,
      final String dynamicStatus,
      final LocalDate today,
      final UUID requestingUserId) {
    final boolean canDl =
        !"EPC".equals(cert.getCertificateType())
            && cert.getDocumentUrl() != null
            && isOwner(cert, requestingUserId);
    return new CertificateListItemResponse(
        cert.getId(),
        cert.getCertificateNumber(),
        cert.getCertificateType(),
        toPropertySummary(cert.getProperty()),
        dynamicStatus,
        cert.getResult(),
        cert.getIssuedAt(),
        cert.getExpiryAt(),
        daysUntilExpiry(cert.getExpiryAt(), today),
        cert.getDocumentUrl(),
        cert.getShareToken(),
        buildShareUrl(cert.getShareToken()),
        canDl,
        true,
        "EXPIRED".equals(dynamicStatus) || "EXPIRING_SOON".equals(dynamicStatus),
        toEngineerSummary(cert));
  }

  private CertificateListItemResponse toMissingListItem(
      final Property property, final String certificateType) {
    return new CertificateListItemResponse(
        null,
        null,
        certificateType,
        toPropertySummary(property),
        "MISSING",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        false,
        true,
        null);
  }

  private static PropertySummaryResponse toPropertySummary(final Property property) {
    if (property == null) return null;
    return new PropertySummaryResponse(
        property.getId(),
        property.getAddressLine1(),
        property.getAddressLine2(),
        property.getCity(),
        property.getPostcode());
  }

  private static EngineerSummaryResponse toEngineerSummary(final Certificate cert) {
    if (cert.getIssuedByEngineer() == null) return null;
    final var engineer = cert.getIssuedByEngineer();
    // Gas safe number is on the GasSafetyRecord (engineerGasSafeNumber); fall back to null
    return new EngineerSummaryResponse(engineer.getId(), engineer.getFullName(), null);
  }

  private static String buildShareUrl(final String shareToken) {
    return shareToken != null ? "/api/v1/certificates/shared/" + shareToken : null;
  }

  private static String buildFilename(final Certificate cert) {
    final String type =
        cert.getCertificateType() != null
            ? cert.getCertificateType().replace("_", "-")
            : "Certificate";
    final String address =
        cert.getProperty() != null
            ? cert.getProperty().getAddressLine1().replaceAll("[^a-zA-Z0-9 ]", "").replace(" ", "_")
            : "Property";
    final String date = cert.getIssuedAt() != null ? cert.getIssuedAt().toString() : "Unknown";
    return type + "_" + address + "_" + date + ".pdf";
  }

  private static void sortItems(final List<CertificateListItemResponse> items, final String sort) {
    if ("expiry_asc".equalsIgnoreCase(sort)) {
      items.sort(
          Comparator.comparing(
              CertificateListItemResponse::expiresAt,
              Comparator.nullsLast(Comparator.naturalOrder())));
      return;
    }
    if ("expiry_desc".equalsIgnoreCase(sort)) {
      items.sort(
          Comparator.comparing(
              CertificateListItemResponse::expiresAt,
              Comparator.nullsFirst(Comparator.reverseOrder())));
      return;
    }
    if ("issued_desc".equalsIgnoreCase(sort)) {
      items.sort(
          Comparator.comparing(
              CertificateListItemResponse::issuedAt,
              Comparator.nullsLast(Comparator.reverseOrder())));
      return;
    }

    // Smart sort: EXPIRED (most overdue first) → EXPIRING_SOON (soonest first) → VALID → MISSING
    items.sort(
        Comparator.comparingInt((CertificateListItemResponse r) -> statusSortOrder(r.status()))
            .thenComparing(
                CertificateListItemResponse::expiresAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
  }

  private static int statusSortOrder(final String status) {
    return switch (status == null ? "" : status) {
      case "EXPIRED" -> 0;
      case "EXPIRING_SOON" -> 1;
      case "VALID" -> 2;
      case "MISSING" -> 3;
      default -> 4;
    };
  }

  private static Meta buildMeta(final List<CertificateListItemResponse> items) {
    int valid = 0, expired = 0, expiringSoon = 0, missing = 0;
    final Map<String, Integer> byType = new HashMap<>();
    for (final CertificateListItemResponse item : items) {
      switch (item.status() == null ? "" : item.status()) {
        case "VALID" -> valid++;
        case "EXPIRED" -> expired++;
        case "EXPIRING_SOON" -> expiringSoon++;
        case "MISSING" -> missing++;
        default -> {
          /* SUPERSEDED etc */
        }
      }
      if (item.certificateType() != null) {
        byType.merge(item.certificateType(), 1, Integer::sum);
      }
    }
    return new Meta(items.size(), new Breakdown(valid, expired, expiringSoon, missing), byType);
  }
}
