package com.uk.certifynow.certify_now.service.certificate;

import com.uk.certifynow.certify_now.config.ShareProperties;
import com.uk.certifynow.certify_now.domain.Certificate;
import com.uk.certifynow.certify_now.domain.CertificateDocument;
import com.uk.certifynow.certify_now.domain.Document;
import com.uk.certifynow.certify_now.domain.EicrInspection;
import com.uk.certifynow.certify_now.domain.GasSafetyRecord;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.ShareToken;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.domain.enums.CertificateStatus;
import com.uk.certifynow.certify_now.domain.enums.CertificateType;
import com.uk.certifynow.certify_now.events.BeforeDeleteCertificate;
import com.uk.certifynow.certify_now.model.NotificationPrefsDTO;
import com.uk.certifynow.certify_now.repos.CertificateRepository;
import com.uk.certifynow.certify_now.repos.DocumentRepository;
import com.uk.certifynow.certify_now.repos.EicrInspectionRepository;
import com.uk.certifynow.certify_now.repos.GasSafetyRecordRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.ShareTokenRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
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
import com.uk.certifynow.certify_now.rest.dto.certificate.ComplianceVaultResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.EngineerSummaryResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.GetCertificatesRequest;
import com.uk.certifynow.certify_now.rest.dto.certificate.MissingCertificateResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.PropertySummaryResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.ShareCertificateResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.UpdateCertificateRequest;
import com.uk.certifynow.certify_now.rest.dto.certificate.UploadCertificateRequest;
import com.uk.certifynow.certify_now.service.enums.UserRole;
import com.uk.certifynow.certify_now.service.storage.DocumentStorageService;
import com.uk.certifynow.certify_now.util.NotFoundException;
import java.io.IOException;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class CustomerCertificateService {

  private static final int EICR_MAX_AGE_YEARS = 5;
  private static final int EPC_MAX_AGE_YEARS = 10;
  private static final int PAT_MAX_AGE_YEARS = 1;
  private final CertificateRepository certificateRepository;
  private final PropertyRepository propertyRepository;
  private final GasSafetyRecordRepository gasSafetyRecordRepository;
  private final EicrInspectionRepository eicrInspectionRepository;
  private final DocumentStorageService documentStorageService;
  private final DocumentRepository documentRepository;
  private final UserRepository userRepository;
  private final ShareTokenRepository shareTokenRepository;
  private final ShareProperties shareProperties;
  private final Clock clock;
  private final ApplicationEventPublisher eventPublisher;

  public CustomerCertificateService(
      final CertificateRepository certificateRepository,
      final PropertyRepository propertyRepository,
      final GasSafetyRecordRepository gasSafetyRecordRepository,
      final EicrInspectionRepository eicrInspectionRepository,
      final DocumentStorageService documentStorageService,
      final DocumentRepository documentRepository,
      final UserRepository userRepository,
      final ShareTokenRepository shareTokenRepository,
      final ShareProperties shareProperties,
      final Clock clock,
      final ApplicationEventPublisher eventPublisher) {
    this.certificateRepository = certificateRepository;
    this.propertyRepository = propertyRepository;
    this.gasSafetyRecordRepository = gasSafetyRecordRepository;
    this.eicrInspectionRepository = eicrInspectionRepository;
    this.documentStorageService = documentStorageService;
    this.documentRepository = documentRepository;
    this.userRepository = userRepository;
    this.shareTokenRepository = shareTokenRepository;
    this.shareProperties = shareProperties;
    this.clock = clock;
    this.eventPublisher = eventPublisher;
  }

  // ── POST /upload ──────────────────────────────────────────────────────────

  @Caching(
      evict = {
        @CacheEvict(value = "customer-certificates", allEntries = true),
        @CacheEvict(value = "my-properties", allEntries = true),
        @CacheEvict(value = "compliance-vault", allEntries = true)
      })
  @Transactional
  public CertificateListItemResponse uploadCertificate(
      final UUID customerId,
      final UploadCertificateRequest request,
      final List<MultipartFile> files) {

    final Property property =
        propertyRepository
            .findByIdAndOwnerId(request.propertyId(), customerId)
            .orElseThrow(() -> new NotFoundException("Property not found"));

    final LocalDate today = LocalDate.now(clock);
    final LocalDate issuedAt = request.issuedAt() != null ? request.issuedAt() : today;

    final String certType =
        "CUSTOM".equals(request.certType())
                && request.customTypeName() != null
                && !request.customTypeName().isBlank()
            ? request.customTypeName().trim()
            : request.certType();

    final String status =
        calculateStatus(request.expiresAt(), CertificateStatus.VALID.name(), today);

    final Certificate cert = new Certificate();
    cert.setCertificateType(certType);
    cert.setProperty(property);
    cert.setIssuedAt(issuedAt);
    cert.setExpiryAt(request.expiresAt());
    cert.setStatus(status);
    cert.setSource("PLATFORM");
    cert.setCreatedAt(OffsetDateTime.now(clock));
    cert.setUpdatedAt(OffsetDateTime.now(clock));

    if (request.notes() != null && !request.notes().isBlank()) {
      cert.setResult(request.notes());
    }

    certificateRepository.save(cert);

    // Sync the denormalised property fields used by ComplianceService.enrich()
    // so that GET /properties/with-compliance reflects the new certificate immediately.
    try {
      switch (CertificateType.valueOf(certType)) {
        case GAS_SAFETY -> {
          property.setHasGasCertificate(true);
          property.setGasExpiryDate(cert.getExpiryAt());
          property.setCurrentGasCertificate(cert);
        }
        case EICR -> {
          property.setHasEicr(true);
          property.setEicrExpiryDate(cert.getExpiryAt());
          property.setCurrentEicrCertificate(cert);
        }
        case EPC -> property.setCurrentEpcCertificate(cert);
        default -> {
          // BOILER_SERVICE, etc. — no property-level compliance fields to sync
        }
      }
    } catch (final IllegalArgumentException e) {
      // CUSTOM type — no property-level compliance fields to sync
    }
    propertyRepository.save(property);

    if (files != null && !files.isEmpty()) {
      final User uploader =
          userRepository
              .findById(customerId)
              .orElseThrow(() -> new NotFoundException("User not found"));

      int order = 0;
      for (final MultipartFile file : files) {
        if (file == null || file.isEmpty()) continue;

        final String originalName =
            file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        final String mimeType =
            file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        final byte[] bytes;
        try {
          bytes = file.getBytes();
        } catch (IOException e) {
          throw new RuntimeException("Failed to read uploaded file", e);
        }
        final String storageUrl =
            documentStorageService.storeRaw(UUID.randomUUID(), originalName, bytes, mimeType);

        Document doc = new Document();
        doc.setFileName(originalName);
        doc.setMimeType(mimeType);
        doc.setFileSizeBytes(file.getSize());
        doc.setStorageUrl(storageUrl);
        doc.setIsVirusScanned(false);
        doc.setUploadedBy(uploader);
        doc = documentRepository.save(doc);

        cert.addDocument(doc, order == 0, order);
        order++;
      }
      certificateRepository.save(cert);
    }

    log.info(
        "Certificate uploaded by customer: customerId={} certId={} type={} propertyId={}",
        customerId,
        cert.getId(),
        certType,
        property.getId());

    final String dynamicStatus = calculateStatus(cert.getExpiryAt(), cert.getStatus(), today);
    return toListItem(cert, dynamicStatus, today, customerId);
  }

  // ── GET /my-certificates ─────────────────────────────────────────────────

  @Cacheable(value = "customer-certificates", key = "{#customerId, #filters}")
  @Transactional(readOnly = true)
  public CertificatesListResponse getCustomerCertificates(
      final UUID customerId, final GetCertificatesRequest filters) {

    final LocalDate today = LocalDate.now(clock);
    final List<Certificate> rawCerts =
        filters.includeHistory()
            ? certificateRepository.findByPropertyOwnerIdWithHistory(
                customerId, filters.type(), filters.propertyId())
            : certificateRepository.findByPropertyOwnerIdWithFilters(
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

  // ── GET /compliance-vault ───────────────────────────────────────────────────

  @Cacheable(value = "compliance-vault", key = "#customerId")
  @Transactional(readOnly = true)
  public ComplianceVaultResponse getComplianceVault(final UUID customerId) {

    final LocalDate today = LocalDate.now(clock);

    // 1. Load all active properties
    final List<Property> properties =
        propertyRepository.findByOwnerIdAndIsActiveTrue(customerId, Sort.by("addressLine1"));

    // 2. Load all non-superseded certificates for those properties
    final List<Certificate> rawCerts =
        certificateRepository.findByPropertyOwnerIdWithFilters(customerId, null, null);

    // 3. Compute dynamic statuses and build list items grouped by property
    final Map<UUID, List<CertificateListItemResponse>> certsByProperty = new HashMap<>();
    final List<CertificateListItemResponse> allItems = new ArrayList<>();
    for (final Certificate cert : rawCerts) {
      final String dynamicStatus = calculateStatus(cert.getExpiryAt(), cert.getStatus(), today);
      final CertificateListItemResponse item = toListItem(cert, dynamicStatus, today, customerId);
      allItems.add(item);
      final UUID pid = cert.getProperty() != null ? cert.getProperty().getId() : null;
      if (pid != null) {
        certsByProperty.computeIfAbsent(pid, k -> new ArrayList<>()).add(item);
      }
    }

    // 4. Detect missing certificates per property
    final Map<String, List<Certificate>> activeCertsByKey =
        batchLoadActiveCerts(properties.stream().map(Property::getId).toList());
    for (final Property property : properties) {
      final List<MissingEntry> missing =
          detectMissingForProperty(property, activeCertsByKey, today);
      for (final MissingEntry entry : missing) {
        final CertificateListItemResponse missingItem =
            toMissingListItem(property, entry.certificateType());
        allItems.add(missingItem);
        certsByProperty.computeIfAbsent(property.getId(), k -> new ArrayList<>()).add(missingItem);
      }
    }

    // 5. Build PropertyWithCertificates list sorted by urgency
    final List<ComplianceVaultResponse.PropertyWithCertificates> propertyList = new ArrayList<>();
    for (final Property property : properties) {
      final List<CertificateListItemResponse> certs =
          certsByProperty.getOrDefault(property.getId(), List.of());
      propertyList.add(
          new ComplianceVaultResponse.PropertyWithCertificates(
              property.getId(),
              property.getAddressLine1(),
              property.getCity(),
              property.getPostcode(),
              property.getHasGasSupply(),
              property.getHasElectric(),
              certs));
    }

    propertyList.sort(
        Comparator.<ComplianceVaultResponse.PropertyWithCertificates>comparingInt(
                p -> propertyUrgencyOrder(p.certificates()))
            .thenComparing(p -> p.addressLine1() != null ? p.addressLine1() : ""));

    // 6. Build meta breakdown from all items
    final Meta meta = buildMeta(allItems);

    return new ComplianceVaultResponse(propertyList, meta);
  }

  private static int propertyUrgencyOrder(final List<CertificateListItemResponse> certs) {
    boolean hasExpired = false;
    boolean hasExpiringSoon = false;
    boolean hasMissing = false;
    for (final CertificateListItemResponse c : certs) {
      final String st = c.status();
      if (CertificateStatus.EXPIRED.name().equals(st)) hasExpired = true;
      else if (CertificateStatus.EXPIRING_SOON.name().equals(st)) hasExpiringSoon = true;
      else if (CertificateStatus.MISSING.name().equals(st)) hasMissing = true;
    }
    if (hasExpired) return 0;
    if (hasExpiringSoon) return 1;
    if (hasMissing) return 2;
    return 3;
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
      switch (cert.getCertificateType() != null ? cert.getCertificateType() : "") {
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
    } else if (CertificateType.EPC.name().equals(cert.getCertificateType())) {
      epcAssessment = new EpcAssessmentSummary(cert.getEpcRating(), cert.getEpcScore(), null, null);
    }

    final boolean canDownload = canDownload(cert, requestingUserId, role);
    final boolean canShare = canShare(cert, requestingUserId, role);
    final boolean canRenew = canRenew(cert, dynamicStatus);

    final List<CertificateDetailResponse.DocumentSummary> documents =
        cert.getDocuments().stream()
            .filter(cd -> cd.getDocument() != null && cd.getDocument().getStorageUrl() != null)
            .sorted(Comparator.comparingInt(cd -> cd.getDisplayOrder()))
            .map(
                cd ->
                    new CertificateDetailResponse.DocumentSummary(
                        cd.getDocument().getId(),
                        cd.getDocument().getStorageUrl(),
                        cd.getDocument().getFileName(),
                        cd.getDocument().getMimeType(),
                        cd.getDocument().getFileSizeBytes()))
            .toList();

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
        getPrimaryDocumentUrl(cert),
        documents,
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

    if (CertificateType.EPC.name().equals(cert.getCertificateType())) {
      throw new IllegalArgumentException(
          "EPC certificates are not downloadable — use the government EPC register.");
    }

    final var primaryDoc = cert.getPrimaryDocument();
    if (primaryDoc == null) {
      throw new NotFoundException("PDF document not found for this certificate");
    }
    final byte[] pdfBytes = documentStorageService.retrieveByUrl(primaryDoc.getStorageUrl());
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

  /** Holds the raw bytes, MIME type, and filename for an uploaded document. */
  public record DocumentContent(byte[] bytes, String mimeType, String filename) {}

  // ── GET /{id}/document ────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public DocumentContent getCertificateDocument(
      final UUID certId, final UUID requestingUserId, final UserRole role) {

    final Certificate cert =
        certificateRepository
            .findByIdWithProperty(certId)
            .orElseThrow(() -> new NotFoundException("Certificate not found"));

    verifyAccess(cert, requestingUserId, role);

    final var primaryDoc = cert.getPrimaryDocument();
    if (primaryDoc == null) {
      throw new NotFoundException("No document attached to this certificate");
    }

    final byte[] bytes = documentStorageService.retrieveByUrl(primaryDoc.getStorageUrl());
    if (bytes == null) {
      throw new NotFoundException("Document file not found in storage");
    }

    final String mimeType =
        primaryDoc.getMimeType() != null ? primaryDoc.getMimeType() : "application/octet-stream";
    final String filename =
        primaryDoc.getFileName() != null ? primaryDoc.getFileName() : "document";

    log.info(
        "Certificate document served: certId={} userId={} filename={}",
        certId,
        requestingUserId,
        filename);
    return new DocumentContent(bytes, mimeType, filename);
  }

  // ── POST /{id}/share ──────────────────────────────────────────────────────

  @Transactional
  public ShareCertificateResponse shareCertificate(final UUID certId, final UUID requestingUserId) {

    final Certificate cert =
        certificateRepository
            .findByIdWithProperty(certId)
            .orElseThrow(() -> new NotFoundException("Certificate not found"));

    verifyOwnership(cert, requestingUserId);

    final OffsetDateTime now = OffsetDateTime.now(clock);

    // Return existing active token (idempotent)
    final List<ShareToken> active = shareTokenRepository.findActiveByCertificateId(certId, now);
    final ShareToken shareToken;
    if (!active.isEmpty()) {
      shareToken = active.get(0);
    } else {
      final User creator =
          userRepository
              .findById(requestingUserId)
              .orElseThrow(() -> new NotFoundException("User not found"));

      final String tokenValue = ShareToken.generateToken();
      final ShareToken newToken = new ShareToken();
      newToken.setToken(tokenValue);
      newToken.setCertificate(cert);
      newToken.setCreatedBy(creator);
      newToken.setCreatedAt(now);
      newToken.setExpiresAt(now.plusDays(shareProperties.getDefaultExpiryDays()));
      shareTokenRepository.save(newToken);
      shareToken = newToken;

      // Keep legacy Certificate.shareToken in sync for existing code paths
      cert.setShareToken(tokenValue);
      cert.setShareTokenCreated(now);
      certificateRepository.save(cert);

      log.info("Share token created for certId={} by userId={}", certId, requestingUserId);
    }

    final String absoluteUrl = shareProperties.getBaseUrl() + "/share/" + shareToken.getToken();
    return new ShareCertificateResponse(
        absoluteUrl, shareToken.getCreatedAt(), shareToken.getExpiresAt());
  }

  // ── DELETE /{id}/share ────────────────────────────────────────────────────

  @Transactional
  public void revokeShare(final UUID certId, final UUID requestingUserId) {
    final Certificate cert =
        certificateRepository
            .findByIdWithProperty(certId)
            .orElseThrow(() -> new NotFoundException("Certificate not found"));

    verifyOwnership(cert, requestingUserId);

    shareTokenRepository.deleteByCertificateId(certId);

    // Clear legacy field on Certificate
    cert.setShareToken(null);
    cert.setShareTokenCreated(null);
    certificateRepository.save(cert);

    log.info("Share token revoked for certId={} by userId={}", certId, requestingUserId);
  }

  // ── DELETE /{certId}/documents/{docId} ───────────────────────────────────

  @Transactional
  public void removeDocument(final UUID certId, final UUID docId, final UUID customerId) {
    final Certificate cert =
        certificateRepository
            .findByIdWithDetails(certId)
            .orElseThrow(() -> new NotFoundException("Certificate not found"));

    verifyOwnership(cert, customerId);

    final CertificateDocument entry =
        cert.getDocuments().stream()
            .filter(cd -> cd.getDocument() != null && docId.equals(cd.getDocument().getId()))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Document not found on this certificate"));

    cert.getDocuments().remove(entry);
    certificateRepository.save(cert);

    documentRepository.deleteById(docId);

    log.info(
        "Document removed from certificate: certId={} docId={} customerId={}",
        certId,
        docId,
        customerId);
  }

  // ── DELETE /{id} ─────────────────────────────────────────────────────────

  @Caching(
      evict = {
        @CacheEvict(value = "customer-certificates", allEntries = true),
        @CacheEvict(value = "my-properties", allEntries = true),
        @CacheEvict(value = "compliance-vault", allEntries = true)
      })
  @Transactional
  public void deleteCertificate(final UUID certId, final UUID customerId) {
    final Certificate cert =
        certificateRepository
            .findByIdWithProperty(certId)
            .orElseThrow(() -> new NotFoundException("Certificate not found"));

    verifyOwnership(cert, customerId);

    // Publishes event so guards (e.g. RenewalReminderService) can block deletion
    // if anything references this certificate.
    eventPublisher.publishEvent(new BeforeDeleteCertificate(certId));

    // Null out any Property.current*Certificate FK that points to this cert
    // before deletion to avoid TransientPropertyValueException on flush.
    // Also clear the denormalised compliance flags so that
    // GET /properties/with-compliance stays in sync.
    final List<Property> referencingProperties =
        propertyRepository.findAllReferencingCertificate(certId);
    for (final Property p : referencingProperties) {
      if (cert.equals(p.getCurrentGasCertificate())) {
        p.setHasGasCertificate(false);
        p.setGasExpiryDate(null);
        p.setCurrentGasCertificate(null);
      }
      if (cert.equals(p.getCurrentEicrCertificate())) {
        p.setHasEicr(false);
        p.setEicrExpiryDate(null);
        p.setCurrentEicrCertificate(null);
      }
      if (cert.equals(p.getCurrentEpcCertificate())) {
        p.setCurrentEpcCertificate(null);
      }
    }
    if (!referencingProperties.isEmpty()) {
      propertyRepository.saveAll(referencingProperties);
    }

    shareTokenRepository.deleteByCertificateId(certId);
    certificateRepository.delete(cert);
    log.info("Certificate deleted: certId={} customerId={}", certId, customerId);
  }

  // ── PATCH /{id} ───────────────────────────────────────────────────────────

  @Caching(
      evict = {
        @CacheEvict(value = "customer-certificates", allEntries = true),
        @CacheEvict(value = "my-properties", allEntries = true),
        @CacheEvict(value = "compliance-vault", allEntries = true)
      })
  @Transactional
  public CertificateListItemResponse updateCertificate(
      final UUID certId, final UUID customerId, final UpdateCertificateRequest request) {

    final Certificate cert =
        certificateRepository
            .findByIdWithProperty(certId)
            .orElseThrow(() -> new NotFoundException("Certificate not found"));

    verifyOwnership(cert, customerId);

    if (request.issuedAt() != null) {
      cert.setIssuedAt(request.issuedAt());
    }
    if (request.expiresAt() != null) {
      cert.setExpiryAt(request.expiresAt());
    }
    if (request.notes() != null) {
      cert.setResult(request.notes().isBlank() ? null : request.notes());
    }
    if (request.customTypeName() != null && !request.customTypeName().isBlank()) {
      cert.setCertificateType(request.customTypeName().trim());
    }
    cert.setUpdatedAt(OffsetDateTime.now(clock));
    certificateRepository.save(cert);

    // Sync denormalised property fields so GET /properties/with-compliance
    // reflects any expiry-date or type changes immediately.
    final Property property = cert.getProperty();
    if (property != null) {
      try {
        switch (CertificateType.valueOf(cert.getCertificateType())) {
          case GAS_SAFETY -> {
            if (cert.equals(property.getCurrentGasCertificate())) {
              property.setGasExpiryDate(cert.getExpiryAt());
            }
          }
          case EICR -> {
            if (cert.equals(property.getCurrentEicrCertificate())) {
              property.setEicrExpiryDate(cert.getExpiryAt());
            }
          }
          case EPC -> {
            // EPC expiry is derived from the Certificate entity via the mapper,
            // so no extra property field to update here.
          }
          default -> {
            // BOILER_SERVICE, etc. — no property-level compliance fields
          }
        }
      } catch (final IllegalArgumentException e) {
        // CUSTOM type — no property-level compliance fields to sync
      }
      propertyRepository.save(property);
    }

    final LocalDate today = LocalDate.now(clock);
    final String dynamicStatus = calculateStatus(cert.getExpiryAt(), cert.getStatus(), today);
    log.info("Certificate updated: certId={} customerId={}", certId, customerId);
    return toListItem(cert, dynamicStatus, today, customerId);
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
          activeCertsByKey.getOrDefault(pid + "|" + CertificateType.GAS_SAFETY.name(), List.of());
      final List<Certificate> nonExpired =
          allActive.stream()
              .filter(c -> c.getExpiryAt() == null || !c.getExpiryAt().isBefore(today))
              .toList();
      if (nonExpired.isEmpty()) {
        entries.add(
            new MissingEntry(
                CertificateType.GAS_SAFETY.name(),
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
          activeCertsByKey.getOrDefault(pid + "|" + CertificateType.EPC.name(), List.of()).stream()
              .filter(c -> c.getExpiryAt() == null || !c.getExpiryAt().isBefore(today))
              .toList();
      final boolean hasValidEpc =
          epcCerts.stream()
              .anyMatch(c -> c.getIssuedAt() != null && c.getIssuedAt().isAfter(epcCutoff));
      if (!hasValidEpc) {
        entries.add(
            new MissingEntry(
                CertificateType.EPC.name(),
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
          activeCertsByKey.getOrDefault(pid + "|" + CertificateType.EICR.name(), List.of()).stream()
              .filter(c -> c.getExpiryAt() == null || !c.getExpiryAt().isBefore(today))
              .toList();
      final boolean hasValidEicr =
          eicrCerts.stream()
              .anyMatch(c -> c.getIssuedAt() != null && c.getIssuedAt().isAfter(eicrCutoff));
      if (!hasValidEicr) {
        entries.add(
            new MissingEntry(
                CertificateType.EICR.name(),
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
          activeCertsByKey.getOrDefault(pid + "|" + CertificateType.PAT.name(), List.of()).stream()
              .filter(c -> c.getExpiryAt() == null || !c.getExpiryAt().isBefore(today))
              .toList();
      final boolean hasValidPat =
          patCerts.stream()
              .anyMatch(c -> c.getIssuedAt() != null && c.getIssuedAt().isAfter(patCutoff));
      if (!hasValidPat) {
        entries.add(
            new MissingEntry(
                CertificateType.PAT.name(),
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
    if (CertificateStatus.SUPERSEDED.name().equals(entityStatus)) {
      return CertificateStatus.SUPERSEDED.name();
    }
    if (CertificateStatus.EXPIRED.name().equals(entityStatus) && expiryAt == null) {
      return CertificateStatus.EXPIRED.name();
    }
    if (expiryAt == null) {
      return CertificateStatus.VALID.name();
    }
    final long days = ChronoUnit.DAYS.between(today, expiryAt);
    if (days < 0) return CertificateStatus.EXPIRED.name();
    if (days < NotificationPrefsDTO.EXPIRING_SOON_THRESHOLD_DAYS)
      return CertificateStatus.EXPIRING_SOON.name();
    return CertificateStatus.VALID.name();
  }

  private static Long daysUntilExpiry(final LocalDate expiryAt, final LocalDate today) {
    if (expiryAt == null) return null;
    return ChronoUnit.DAYS.between(today, expiryAt);
  }

  private boolean canDownload(
      final Certificate cert, final UUID requestingUserId, final UserRole role) {
    if (CertificateType.EPC.name().equals(cert.getCertificateType())) return false;
    if (cert.getPrimaryDocument() == null) return false;
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
    return CertificateStatus.EXPIRED.name().equals(dynamicStatus)
        || CertificateStatus.EXPIRING_SOON.name().equals(dynamicStatus);
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
        !CertificateType.EPC.name().equals(cert.getCertificateType())
            && cert.getPrimaryDocument() != null
            && isOwner(cert, requestingUserId);
    return new CertificateListItemResponse(
        cert.getId(),
        cert.getCertificateNumber(),
        cert.getCertificateType(),
        toPropertySummary(cert.getProperty()),
        dynamicStatus,
        cert.getResult(),
        cert.getEpcRating(),
        cert.getIssuedAt(),
        cert.getExpiryAt(),
        daysUntilExpiry(cert.getExpiryAt(), today),
        getPrimaryDocumentUrl(cert),
        cert.getShareToken(),
        buildShareUrl(cert.getShareToken()),
        canDl,
        true,
        CertificateStatus.EXPIRED.name().equals(dynamicStatus)
            || CertificateStatus.EXPIRING_SOON.name().equals(dynamicStatus),
        toEngineerSummary(cert),
        cert.getSupersededBy() != null ? cert.getSupersededBy().getId() : null);
  }

  private CertificateListItemResponse toMissingListItem(
      final Property property, final String certificateType) {
    return new CertificateListItemResponse(
        null,
        null,
        certificateType,
        toPropertySummary(property),
        CertificateStatus.MISSING.name(),
        null,
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
        null,
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

  private static String getPrimaryDocumentUrl(final Certificate cert) {
    final var primary = cert.getPrimaryDocument();
    return primary != null ? primary.getStorageUrl() : null;
  }

  private static String buildShareUrl(final String shareToken) {
    return shareToken != null ? "/share/" + shareToken : null;
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
    if (status == null) return 4;
    if (CertificateStatus.EXPIRED.name().equals(status)) return 0;
    if (CertificateStatus.EXPIRING_SOON.name().equals(status)) return 1;
    if (CertificateStatus.VALID.name().equals(status)) return 2;
    if (CertificateStatus.MISSING.name().equals(status)) return 3;
    return 4;
  }

  private static Meta buildMeta(final List<CertificateListItemResponse> items) {
    int valid = 0, expired = 0, expiringSoon = 0, missing = 0, superseded = 0;
    final Map<String, Integer> byType = new HashMap<>();
    for (final CertificateListItemResponse item : items) {
      final String st = item.status() == null ? "" : item.status();
      if (CertificateStatus.VALID.name().equals(st)) valid++;
      else if (CertificateStatus.EXPIRED.name().equals(st)) expired++;
      else if (CertificateStatus.EXPIRING_SOON.name().equals(st)) expiringSoon++;
      else if (CertificateStatus.MISSING.name().equals(st)) missing++;
      else if (CertificateStatus.SUPERSEDED.name().equals(st)) superseded++;
      if (item.certificateType() != null) {
        byType.merge(item.certificateType(), 1, Integer::sum);
      }
    }
    return new Meta(
        items.size(), new Breakdown(valid, expired, expiringSoon, missing, superseded), byType);
  }
}
