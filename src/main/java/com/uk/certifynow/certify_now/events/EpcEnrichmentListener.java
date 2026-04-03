package com.uk.certifynow.certify_now.events;

import com.uk.certifynow.certify_now.domain.Certificate;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.enums.CertificateStatus;
import com.uk.certifynow.certify_now.repos.CertificateRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.service.epc.EpcLookupService;
import com.uk.certifynow.certify_now.service.notification.SseEmitterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Looks up EPC data from the government registry after a property is successfully created.
 *
 * <p>Runs on a dedicated thread pool ({@code epcTaskExecutor}) after the property creation
 * transaction has committed ({@code AFTER_COMMIT}), so the HTTP response is already back to the
 * client and the property row is visible to this new transaction.
 *
 * <p>Business rules:
 *
 * <ul>
 *   <li>No UPRN on the property → nothing to do, EPC remains MISSING on the compliance screen.
 *   <li>EPC found in registry and still valid → {@code Certificate} created with status {@code
 *       ACTIVE}. Expiry = lodgement date + 10 years.
 *   <li>EPC found but already expired → {@code Certificate} created with status {@code EXPIRED}.
 *   <li>No EPC found for this UPRN → {@code Certificate} created with status {@code EXPIRED} and no
 *       expiry date, signalling non-compliance.
 * </ul>
 */
@Component
public class EpcEnrichmentListener {

  private static final Logger log = LoggerFactory.getLogger(EpcEnrichmentListener.class);

  private static final int EPC_VALID_YEARS = 10;
  private static final String CERT_TYPE_EPC = "EPC";
  private static final String SOURCE_GOVERNMENT = "GOVERNMENT";

  private final EpcLookupService epcLookupService;
  private final PropertyRepository propertyRepository;
  private final CertificateRepository certificateRepository;
  private final Clock clock;
  private final SseEmitterRegistry sseEmitterRegistry;
  private final CacheManager cacheManager;

  public EpcEnrichmentListener(
      final EpcLookupService epcLookupService,
      final PropertyRepository propertyRepository,
      final CertificateRepository certificateRepository,
      final Clock clock,
      final SseEmitterRegistry sseEmitterRegistry,
      final CacheManager cacheManager) {
    this.epcLookupService = epcLookupService;
    this.propertyRepository = propertyRepository;
    this.certificateRepository = certificateRepository;
    this.clock = clock;
    this.sseEmitterRegistry = sseEmitterRegistry;
    this.cacheManager = cacheManager;
  }

  @Async("epcTaskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onPropertyCreated(final PropertyCreatedEvent event) {
    final Optional<Property> maybeProperty = propertyRepository.findById(event.getPropertyId());
    if (maybeProperty.isEmpty()) {
      log.warn("EPC enrichment: property {} not found after commit", event.getPropertyId());
      return;
    }

    final Property property = maybeProperty.get();
    final String uprn = property.getUprn();

    if (uprn == null || uprn.isBlank()) {
      log.debug("EPC enrichment: property {} has no UPRN — skipping EPC lookup", property.getId());
      return;
    }

    log.info("EPC enrichment: starting lookup for property={} uprn={}", property.getId(), uprn);

    final EpcLookupService.EpcRecord record = epcLookupService.lookup(uprn);

    if (record == null) {
      log.info(
          "EPC enrichment: no EPC found in government registry for property={} uprn={} — leaving as MISSING",
          property.getId(),
          uprn);
      return;
    }

    final Certificate cert = buildCertificate(property, uprn, record);

    final Certificate saved = certificateRepository.save(cert);
    property.setCurrentEpcCertificate(saved);
    propertyRepository.save(property);

    log.info(
        "EPC enrichment: complete for property={} uprn={} status={} rating={} expiry={}",
        property.getId(),
        uprn,
        saved.getStatus(),
        saved.getEpcRating(),
        saved.getExpiryAt());

    // Evict the cached list response and push SSE after this transaction commits so the UI
    // refetch always sees the committed EPC data. Cache is cleared before the SSE push so
    // that the refetch triggered by the notification never hits stale data.
    // TransactionSynchronizationManager fires afterCommit() on the REQUIRES_NEW transaction,
    // not the original property-creation transaction.
    final UUID ownerId = event.getOwnerId();
    final String propertyId = property.getId().toString();
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            final var cache = cacheManager.getCache("my-properties");
            if (cache != null) {
              cache.evictIfPresent(ownerId);
            }
            sseEmitterRegistry.push(ownerId, "epc-enriched", Map.of("propertyId", propertyId));
          }
        });
  }

  private Certificate buildCertificate(
      final Property property, final String uprn, final EpcLookupService.EpcRecord record) {
    final OffsetDateTime now = OffsetDateTime.now(clock);
    final LocalDate today = now.toLocalDate();

    final LocalDate expiryDate = record.registrationDate().plusYears(EPC_VALID_YEARS);
    final boolean active = expiryDate.isAfter(today);

    final Certificate cert = new Certificate();
    cert.setProperty(property);
    cert.setCertificateType(CERT_TYPE_EPC);
    cert.setSource(SOURCE_GOVERNMENT);
    cert.setCreatedAt(now);
    cert.setUpdatedAt(now);
    cert.setValidYears(EPC_VALID_YEARS);
    cert.setIssuedAt(record.registrationDate());
    cert.setExpiryAt(expiryDate);
    cert.setEpcRating(record.energyBand());
    cert.setCertificateNumber(record.certificateNumber());
    cert.setStatus(active ? CertificateStatus.ACTIVE.name() : CertificateStatus.EXPIRED.name());

    return cert;
  }
}
