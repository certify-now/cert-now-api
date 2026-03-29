package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteProperty;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.events.PropertyCreatedEvent;
import com.uk.certifynow.certify_now.events.PropertyRestoredEvent;
import com.uk.certifynow.certify_now.events.PropertySoftDeletedEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.model.ComplianceHealthDTO;
import com.uk.certifynow.certify_now.model.MyPropertiesResponse;
import com.uk.certifynow.certify_now.model.PropertyDTO;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.rest.dto.property.CreatePropertyRequest;
import com.uk.certifynow.certify_now.rest.dto.property.UpdatePropertyRequest;
import com.uk.certifynow.certify_now.service.job.JobStatus;
import com.uk.certifynow.certify_now.service.mappers.PropertyMapper;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PropertyService {

  /** WGS84 SRID — must match the geography column definition in PostGIS. */
  private static final int SRID_WGS84 = 4326;

  private static final GeometryFactory GEO_FACTORY =
      new GeometryFactory(new PrecisionModel(), SRID_WGS84);

  private final PropertyRepository propertyRepository;
  private final UserRepository userRepository;
  private final JobRepository jobRepository;
  private final ApplicationEventPublisher publisher;
  private final PropertyMapper propertyMapper;
  private final ComplianceService complianceService;
  private final AddressLookupService addressLookupService;
  private final Clock clock;

  public PropertyService(
      final PropertyRepository propertyRepository,
      final UserRepository userRepository,
      final JobRepository jobRepository,
      final ApplicationEventPublisher publisher,
      final PropertyMapper propertyMapper,
      final ComplianceService complianceService,
      final AddressLookupService addressLookupService,
      final Clock clock) {
    this.propertyRepository = propertyRepository;
    this.userRepository = userRepository;
    this.jobRepository = jobRepository;
    this.publisher = publisher;
    this.propertyMapper = propertyMapper;
    this.complianceService = complianceService;
    this.addressLookupService = addressLookupService;
    this.clock = clock;
  }

  public Page<PropertyDTO> findAll(final Pageable pageable) {
    return propertyRepository.findAll(pageable).map(p -> enriched(propertyMapper.toDTO(p)));
  }

  public Page<PropertyDTO> findAllIncludingDeleted(final Pageable pageable) {
    return propertyRepository
        .findAllIncludingDeletedPaged(pageable)
        .map(p -> enriched(propertyMapper.toDTO(p)));
  }

  public Page<PropertyDTO> findAllDeleted(final Pageable pageable) {
    return propertyRepository
        .findAllDeletedPaged(pageable)
        .map(p -> enriched(propertyMapper.toDTO(p)));
  }

  public PropertyDTO get(final UUID id) {
    return propertyRepository
        .findById(id)
        .map(p -> enriched(propertyMapper.toDTO(p)))
        .orElseThrow(NotFoundException::new);
  }

  public PropertyDTO getForOwner(final UUID id, final UUID userId) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    assertOwnership(property, userId);
    return enriched(propertyMapper.toDTO(property));
  }

  public Page<PropertyDTO> getByOwner(final UUID ownerId, final Pageable pageable) {
    return propertyRepository
        .findByOwnerIdAndIsActiveTrue(ownerId, pageable)
        .map(p -> enriched(propertyMapper.toDTO(p)));
  }

  /**
   * Returns all active properties for an owner together with the aggregate compliance health. This
   * is the primary endpoint for the properties list screen.
   */
  @Cacheable(value = "my-properties", key = "#ownerId")
  public MyPropertiesResponse getMyPropertiesWithCompliance(final UUID ownerId) {
    final List<PropertyDTO> properties =
        propertyRepository
            .findByOwnerIdAndIsActiveTrue(ownerId, Sort.by("createdAt").descending())
            .stream()
            .map(p -> enriched(propertyMapper.toDTO(p)))
            .toList();
    final ComplianceHealthDTO health = complianceService.computeHealth(properties);
    return new MyPropertiesResponse(properties, health);
  }

  @CacheEvict(value = "my-properties", allEntries = true)
  @Transactional
  public PropertyDTO create(final CreatePropertyRequest request, final UUID ownerId) {
    final User owner =
        userRepository
            .findById(ownerId)
            .orElseThrow(() -> new NotFoundException("owner not found"));

    if (propertyRepository.existsByOwnerIdAndAddressLine1IgnoreCaseAndPostcodeIgnoreCase(
        ownerId, request.addressLine1().trim(), request.postcode().trim())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "DUPLICATE_PROPERTY",
          "A property at this address is already registered to your account.");
    }

    final Property property = propertyMapper.toEntity(request);
    property.setOwner(owner);
    property.setIsActive(true);
    property.setComplianceStatus("MISSING");
    property.setCoordinates(resolveCoordinates(request));
    final OffsetDateTime now = OffsetDateTime.now(clock);
    property.setCreatedAt(now);
    property.setUpdatedAt(now);

    final Property saved = propertyRepository.save(property);
    log.info("Property {} created for owner {}", saved.getId(), ownerId);
    publisher.publishEvent(new PropertyCreatedEvent(saved.getId(), ownerId));
    return propertyMapper.toDTO(saved);
  }

  @CacheEvict(value = "my-properties", allEntries = true)
  @Transactional
  public PropertyDTO update(final UUID id, final UpdatePropertyRequest request, final UUID userId) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    assertOwnership(property, userId);

    if (propertyRepository.existsByOwnerIdAndAddressLine1IgnoreCaseAndPostcodeIgnoreCaseAndIdNot(
        userId, request.addressLine1().trim(), request.postcode().trim(), id)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "DUPLICATE_PROPERTY",
          "A property at this address is already registered to your account.");
    }

    propertyMapper.updateEntity(request, property);
    property.setUpdatedAt(OffsetDateTime.now(clock));
    final Property saved = propertyRepository.save(property);
    log.info("Property {} updated", id);
    return enriched(propertyMapper.toDTO(saved));
  }

  @CacheEvict(value = "my-properties", allEntries = true)
  @Transactional
  public void deactivate(final UUID id, final UUID userId) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    assertOwnership(property, userId);
    property.setIsActive(false);
    propertyRepository.save(property);
    log.info("Property {} deactivated", id);
  }

  @CacheEvict(value = "my-properties", allEntries = true)
  @Transactional
  public void delete(final UUID id) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    publisher.publishEvent(new BeforeDeleteProperty(id));
    propertyRepository.delete(property);
    log.info("Property {} deleted", id);
  }

  // ── Soft-delete operations ──────────────────────────────────────────────────

  @CacheEvict(value = "my-properties", allEntries = true)
  @Transactional
  public void softDelete(final UUID id, final UUID deletedByUserId) {
    final Property property =
        propertyRepository.findByIdIncludingDeleted(id).orElseThrow(NotFoundException::new);

    if (property.isDeleted()) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "ALREADY_DELETED", "Property is already soft-deleted");
    }

    final boolean hasActiveJobs =
        jobRepository.existsByPropertyIdAndStatusNotIn(id, JobStatus.TERMINAL_STATUSES);
    if (hasActiveJobs) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "ACTIVE_JOBS_EXIST", "Cannot soft-delete property with active jobs");
    }

    final OffsetDateTime now = OffsetDateTime.now(clock);
    property.setDeletedAt(now);
    property.setDeletedBy(deletedByUserId);
    propertyRepository.save(property);

    log.info("Property {} soft-deleted by {}", id, deletedByUserId);
    publisher.publishEvent(new PropertySoftDeletedEvent(id, deletedByUserId));
  }

  @CacheEvict(value = "my-properties", allEntries = true)
  @Transactional
  public PropertyDTO restore(final UUID id, final UUID restoredByUserId) {
    final Property property =
        propertyRepository.findByIdIncludingDeleted(id).orElseThrow(NotFoundException::new);

    if (!property.isDeleted()) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "NOT_DELETED", "Property is not soft-deleted");
    }

    property.setDeletedAt(null);
    property.setDeletedBy(null);
    final Property saved = propertyRepository.save(property);

    log.info("Property {} restored by {}", id, restoredByUserId);
    publisher.publishEvent(new PropertyRestoredEvent(id, restoredByUserId));

    return enriched(propertyMapper.toDTO(saved));
  }

  // ── Certificate metadata updates ─────────────────────────────────────────
  // PDFs are now stored in object storage (Minio/S3) via Document entities.
  // These endpoints update only the denormalised metadata flags used for quick
  // compliance checks; file upload is handled by the ComplianceDocument flow.

  @CacheEvict(value = "my-properties", allEntries = true)
  @Transactional
  public PropertyDTO updateGasCertificateMetadata(
      final UUID id,
      final Boolean hasGasCertificate,
      final LocalDate gasExpiryDate,
      final UUID userId) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    assertOwnership(property, userId);
    if (hasGasCertificate != null) property.setHasGasCertificate(hasGasCertificate);
    if (gasExpiryDate != null) property.setGasExpiryDate(gasExpiryDate);
    property.setUpdatedAt(OffsetDateTime.now(clock));
    final Property saved = propertyRepository.save(property);
    log.info("Gas certificate metadata updated for property {}", id);
    return enriched(propertyMapper.toDTO(saved));
  }

  @CacheEvict(value = "my-properties", allEntries = true)
  @Transactional
  public PropertyDTO updateEicrMetadata(
      final UUID id, final Boolean hasEicr, final LocalDate eicrExpiryDate, final UUID userId) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    assertOwnership(property, userId);
    if (hasEicr != null) property.setHasEicr(hasEicr);
    if (eicrExpiryDate != null) property.setEicrExpiryDate(eicrExpiryDate);
    property.setUpdatedAt(OffsetDateTime.now(clock));
    final Property saved = propertyRepository.save(property);
    log.info("EICR metadata updated for property {}", id);
    return enriched(propertyMapper.toDTO(saved));
  }

  @EventListener(BeforeDeleteUser.class)
  public void on(final BeforeDeleteUser event) {
    final ReferencedException referencedException = new ReferencedException();
    final Property ownerProperty = propertyRepository.findFirstByOwnerId(event.getId());
    if (ownerProperty != null) {
      referencedException.setKey("user.property.owner.referenced");
      referencedException.addParam(ownerProperty.getId());
      throw referencedException;
    }
  }

  /**
   * Resolves WGS84 coordinates for a new property.
   *
   * <ol>
   *   <li>If the request already carries {@code latitude}/{@code longitude} (populated by the
   *       autocomplete resolve call in the UI), use them directly.
   *   <li>Otherwise fall back to the Ideal Postcodes postcode centroid endpoint to ensure every
   *       property always has coordinates for PostGIS engineer-radius queries.
   * </ol>
   */
  private Point resolveCoordinates(final CreatePropertyRequest request) {
    if (request.latitude() != null && request.longitude() != null) {
      return buildPoint(request.latitude(), request.longitude());
    }

    log.debug(
        "No coordinates in create request for postcode {}; falling back to centroid lookup",
        request.postcode());

    final double[] centroid = addressLookupService.lookupPostcodeCentroid(request.postcode());
    if (centroid != null) {
      return buildPoint(centroid[0], centroid[1]);
    }

    log.warn(
        "Could not resolve coordinates for postcode {} — property will be stored without location",
        request.postcode());
    return null;
  }

  /**
   * Creates a JTS {@link Point} in WGS84 (SRID 4326). Note: JTS uses (longitude, latitude) order.
   */
  private static Point buildPoint(final double latitude, final double longitude) {
    final Point point = GEO_FACTORY.createPoint(new Coordinate(longitude, latitude));
    point.setSRID(SRID_WGS84);
    return point;
  }

  private void assertOwnership(final Property property, final UUID userId) {
    if (property.getOwner() == null || !userId.equals(property.getOwner().getId())) {
      throw new AccessDeniedException("You do not own this property");
    }
  }

  private PropertyDTO enriched(final PropertyDTO dto) {
    complianceService.enrich(dto);
    return dto;
  }
}
