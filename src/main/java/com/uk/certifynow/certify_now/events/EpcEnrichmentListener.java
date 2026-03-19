package com.uk.certifynow.certify_now.events;

import com.uk.certifynow.certify_now.domain.Certificate;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.repos.CertificateRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.service.EpcLookupService;
import com.uk.certifynow.certify_now.service.EpcLookupService.EpcRecord;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Looks up EPC data from the government registry after a property is successfully created.
 *
 * <p>Runs on a dedicated thread pool ({@code epcTaskExecutor}) after the property creation
 * transaction has committed ({@code AFTER_COMMIT}), so the HTTP response is already back to the
 * client and the property row is visible to this new transaction.
 *
 * <p>Business rules:
 * <ul>
 *   <li>No UPRN on the property → nothing to do, EPC remains MISSING on the compliance screen.</li>
 *   <li>EPC found in registry and still valid → {@code Certificate} created with status
 *       {@code ACTIVE}. Expiry = lodgement date + 10 years.</li>
 *   <li>EPC found but already expired → {@code Certificate} created with status {@code EXPIRED}.</li>
 *   <li>No EPC found for this UPRN → {@code Certificate} created with status {@code EXPIRED} and
 *       no expiry date, signalling non-compliance.</li>
 * </ul>
 */
@Component
public class EpcEnrichmentListener {

  private static final Logger log = LoggerFactory.getLogger(EpcEnrichmentListener.class);

  private static final int EPC_VALID_YEARS = 10;
  private static final String CERT_TYPE_EPC = "EPC";
  private static final String SOURCE_GOVERNMENT = "GOVERNMENT";
  private static final String STATUS_ACTIVE = "ACTIVE";
  private static final String STATUS_EXPIRED = "EXPIRED";

  private final EpcLookupService epcLookupService;
  private final PropertyRepository propertyRepository;
  private final CertificateRepository certificateRepository;
  private final Clock clock;

  public EpcEnrichmentListener(
      final EpcLookupService epcLookupService,
      final PropertyRepository propertyRepository,
      final CertificateRepository certificateRepository,
      final Clock clock) {
    this.epcLookupService = epcLookupService;
    this.propertyRepository = propertyRepository;
    this.certificateRepository = certificateRepository;
    this.clock = clock;
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

    final EpcRecord record = epcLookupService.lookup(uprn);
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
  }

  private Certificate buildCertificate(final Property property, final String uprn, final EpcRecord record) {
    final OffsetDateTime now = OffsetDateTime.now(clock);
    final LocalDate today = now.toLocalDate();

    final Certificate cert = new Certificate();
    cert.setProperty(property);
    cert.setCertificateType(CERT_TYPE_EPC);
    cert.setSource(SOURCE_GOVERNMENT);
    cert.setCreatedAt(now);
    cert.setUpdatedAt(now);
    cert.setValidYears(EPC_VALID_YEARS);

    if (record == null) {
      // No EPC on record — mark as expired/non-compliant
      cert.setIssuedAt(today);
      cert.setStatus(STATUS_EXPIRED);
      log.warn("EPC enrichment: no EPC found in registry for property={} uprn={} — marking as EXPIRED", property.getId(), uprn);
      return cert;
    }

    final LocalDate expiryDate = record.registrationDate().plusYears(EPC_VALID_YEARS);
    final boolean active = expiryDate.isAfter(today);

    cert.setIssuedAt(record.registrationDate());
    cert.setExpiryAt(expiryDate);
    cert.setEpcRating(record.energyBand());
    cert.setStatus(active ? STATUS_ACTIVE : STATUS_EXPIRED);

    return cert;
  }
}
