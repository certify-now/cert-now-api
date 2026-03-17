package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteProperty;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.events.PropertyRestoredEvent;
import com.uk.certifynow.certify_now.events.PropertySoftDeletedEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.model.ComplianceHealthDTO;
import com.uk.certifynow.certify_now.model.MyPropertiesResponse;
import com.uk.certifynow.certify_now.model.PropertyDTO;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.mappers.PropertyMapper;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class PropertyService {

  private static final List<String> TERMINAL_STATUSES =
      List.of("COMPLETED", "CERTIFIED", "CANCELLED", "FAILED");

  private final PropertyRepository propertyRepository;
  private final UserRepository userRepository;
  private final JobRepository jobRepository;
  private final ApplicationEventPublisher publisher;
  private final PropertyMapper propertyMapper;
  private final ComplianceService complianceService;

  public PropertyService(
      final PropertyRepository propertyRepository,
      final UserRepository userRepository,
      final JobRepository jobRepository,
      final ApplicationEventPublisher publisher,
      final PropertyMapper propertyMapper,
      final ComplianceService complianceService) {
    this.propertyRepository = propertyRepository;
    this.userRepository = userRepository;
    this.jobRepository = jobRepository;
    this.publisher = publisher;
    this.propertyMapper = propertyMapper;
    this.complianceService = complianceService;
  }

  public List<PropertyDTO> findAll() {
    final List<Property> properties = propertyRepository.findAll(Sort.by("id"));
    return properties.stream().map(p -> enriched(propertyMapper.toDTO(p))).toList();
  }

  public PropertyDTO get(final UUID id) {
    return propertyRepository
        .findById(id)
        .map(p -> enriched(propertyMapper.toDTO(p)))
        .orElseThrow(NotFoundException::new);
  }

  public org.springframework.data.domain.Page<PropertyDTO> getByOwner(
      final UUID ownerId, final org.springframework.data.domain.Pageable pageable) {
    return propertyRepository
        .findByOwnerIdAndIsActiveTrue(ownerId, pageable)
        .map(p -> enriched(propertyMapper.toDTO(p)));
  }

  /**
   * Returns all active properties for an owner together with the aggregate compliance health. This
   * is the primary endpoint for the properties list screen.
   */
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

  @Transactional
  public PropertyDTO create(
      final PropertyDTO propertyDTO,
      final MultipartFile gasCertPdf,
      final MultipartFile eicrCertPdf) {
    // Resolve owner first so we can check for duplicates before creating the entity
    final User owner =
        propertyDTO.getOwner() == null
            ? null
            : userRepository
                .findById(propertyDTO.getOwner())
                .orElseThrow(() -> new NotFoundException("owner not found"));

    if (owner != null
        && propertyDTO.getAddressLine1() != null
        && propertyDTO.getPostcode() != null
        && propertyRepository.existsByOwnerIdAndAddressLine1IgnoreCaseAndPostcodeIgnoreCase(
            owner.getId(),
            propertyDTO.getAddressLine1().trim(),
            propertyDTO.getPostcode().trim())) {
      throw new BusinessException(
          org.springframework.http.HttpStatus.CONFLICT,
          "DUPLICATE_PROPERTY",
          "A property at this address is already registered to your account.");
    }

    final Property property = new Property();
    propertyMapper.updateEntity(propertyDTO, property);
    property.setOwner(owner);
    property.setIsActive(true);
    if (property.getId() == null) {
      final java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
      property.setCreatedAt(now);
      property.setUpdatedAt(now);
    }
    attachPdfs(property, gasCertPdf, eicrCertPdf);
    Property saved = propertyRepository.save(property);
    if (saved.getOwner() != null) {
      log.info("Property {} created for owner {}", saved.getId(), saved.getOwner().getId());
      publisher.publishEvent(
          new com.uk.certifynow.certify_now.events.PropertyCreatedEvent(
              saved.getId(), saved.getOwner().getId()));
    }
    return propertyMapper.toDTO(saved);
  }

  @Transactional
  public PropertyDTO update(final UUID id, final PropertyDTO propertyDTO) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    final java.time.OffsetDateTime createdAt = property.getCreatedAt();

    // Resolve owner reference from UUID before the duplicate check
    final User owner =
        propertyDTO.getOwner() == null
            ? null
            : userRepository
                .findById(propertyDTO.getOwner())
                .orElseThrow(() -> new NotFoundException("owner not found"));

    if (owner != null
        && propertyDTO.getAddressLine1() != null
        && propertyDTO.getPostcode() != null
        && propertyRepository.existsByOwnerIdAndAddressLine1IgnoreCaseAndPostcodeIgnoreCaseAndIdNot(
            owner.getId(),
            propertyDTO.getAddressLine1().trim(),
            propertyDTO.getPostcode().trim(),
            id)) {
      throw new BusinessException(
          org.springframework.http.HttpStatus.CONFLICT,
          "DUPLICATE_PROPERTY",
          "A property at this address is already registered to your account.");
    }

    propertyMapper.updateEntity(propertyDTO, property);
    property.setOwner(owner);
    property.setCreatedAt(createdAt);
    property.setUpdatedAt(java.time.OffsetDateTime.now());
    final Property saved = propertyRepository.save(property);
    log.info("Property {} updated", id);
    return enriched(propertyMapper.toDTO(saved));
  }

  @Transactional
  public void deactivate(final UUID id) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    property.setIsActive(false);
    propertyRepository.save(property);
    log.info("Property {} deactivated", id);
  }

  @Transactional
  public void delete(final UUID id) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    publisher.publishEvent(new BeforeDeleteProperty(id));
    propertyRepository.delete(property);
    log.info("Property {} deleted", id);
  }

  // ── Soft-delete operations ──────────────────────────────────────────────────

  /**
   * Soft-deletes a property by setting deletedAt/deletedBy. Validates there are no active
   * (non-terminal) jobs referencing this property.
   */
  @Transactional
  public void softDelete(final UUID id, final UUID deletedByUserId) {
    final Property property =
        propertyRepository.findByIdIncludingDeleted(id).orElseThrow(NotFoundException::new);

    if (property.isDeleted()) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "ALREADY_DELETED", "Property is already soft-deleted");
    }

    // Validate: no active (non-terminal) jobs on this property
    final boolean hasActiveJobs =
        jobRepository.existsByPropertyIdAndStatusNotIn(id, TERMINAL_STATUSES);
    if (hasActiveJobs) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "ACTIVE_JOBS_EXIST", "Cannot soft-delete property with active jobs");
    }

    final OffsetDateTime now = OffsetDateTime.now();
    property.setDeletedAt(now);
    property.setDeletedBy(deletedByUserId);
    propertyRepository.save(property);

    log.info("Property {} soft-deleted by {}", id, deletedByUserId);
    publisher.publishEvent(new PropertySoftDeletedEvent(id, deletedByUserId));
  }

  /** Restores a soft-deleted property by clearing deletedAt/deletedBy. Admin only. */
  @Transactional
  public void restore(final UUID id, final UUID restoredByUserId) {
    final Property property =
        propertyRepository.findByIdIncludingDeleted(id).orElseThrow(NotFoundException::new);

    if (!property.isDeleted()) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "NOT_DELETED", "Property is not soft-deleted");
    }

    property.setDeletedAt(null);
    property.setDeletedBy(null);
    propertyRepository.save(property);

    log.info("Property {} restored by {}", id, restoredByUserId);
    publisher.publishEvent(new PropertyRestoredEvent(id, restoredByUserId));
  }

  /** Upload or update the Gas Safety Certificate PDF and/or expiry metadata for a property. */
  @Transactional
  public PropertyDTO uploadGasCertificate(
      final UUID id,
      final Boolean hasGasCertificate,
      final java.time.LocalDate gasExpiryDate,
      final MultipartFile pdfFile) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    if (hasGasCertificate != null) {
      property.setHasGasCertificate(hasGasCertificate);
    }
    if (gasExpiryDate != null) {
      property.setGasExpiryDate(gasExpiryDate);
    }
    if (pdfFile != null && !pdfFile.isEmpty()) {
      try {
        property.setGasCertPdf(pdfFile.getBytes());
        property.setGasCertPdfName(pdfFile.getOriginalFilename());
      } catch (IOException e) {
        throw new RuntimeException("Failed to read gas cert PDF", e);
      }
    }
    property.setUpdatedAt(java.time.OffsetDateTime.now());
    final Property saved = propertyRepository.save(property);
    log.info("Gas certificate updated for property {}", id);
    return enriched(propertyMapper.toDTO(saved));
  }

  /** Upload or update the EICR PDF and/or expiry metadata for a property. */
  @Transactional
  public PropertyDTO uploadEicrCertificate(
      final UUID id,
      final Boolean hasEicr,
      final java.time.LocalDate eicrExpiryDate,
      final MultipartFile pdfFile) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    if (hasEicr != null) {
      property.setHasEicr(hasEicr);
    }
    if (eicrExpiryDate != null) {
      property.setEicrExpiryDate(eicrExpiryDate);
    }
    if (pdfFile != null && !pdfFile.isEmpty()) {
      try {
        property.setEicrCertPdf(pdfFile.getBytes());
        property.setEicrCertPdfName(pdfFile.getOriginalFilename());
      } catch (IOException e) {
        throw new RuntimeException("Failed to read EICR PDF", e);
      }
    }
    property.setUpdatedAt(java.time.OffsetDateTime.now());
    final Property saved = propertyRepository.save(property);
    log.info("EICR certificate updated for property {}", id);
    return enriched(propertyMapper.toDTO(saved));
  }

  /** Returns the raw Gas Safety certificate PDF bytes for the given property. */
  public byte[] getGasCertPdf(final UUID id) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    if (property.getGasCertPdf() == null || property.getGasCertPdf().length == 0) {
      throw new NotFoundException("No gas certificate PDF found");
    }
    return property.getGasCertPdf();
  }

  /** Returns the raw EICR certificate PDF bytes for the given property. */
  public byte[] getEicrCertPdf(final UUID id) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    if (property.getEicrCertPdf() == null || property.getEicrCertPdf().length == 0) {
      throw new NotFoundException("No EICR certificate PDF found");
    }
    return property.getEicrCertPdf();
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

  private PropertyDTO enriched(final PropertyDTO dto) {
    complianceService.enrich(dto);
    return dto;
  }

  private void attachPdfs(
      final Property property, final MultipartFile gasCertPdf, final MultipartFile eicrCertPdf) {
    if (gasCertPdf != null && !gasCertPdf.isEmpty()) {
      try {
        property.setGasCertPdf(gasCertPdf.getBytes());
        property.setGasCertPdfName(gasCertPdf.getOriginalFilename());
      } catch (IOException e) {
        throw new RuntimeException("Failed to read gas cert PDF", e);
      }
    }
    if (eicrCertPdf != null && !eicrCertPdf.isEmpty()) {
      try {
        property.setEicrCertPdf(eicrCertPdf.getBytes());
        property.setEicrCertPdfName(eicrCertPdf.getOriginalFilename());
      } catch (IOException e) {
        throw new RuntimeException("Failed to read EICR PDF", e);
      }
    }
  }
}
