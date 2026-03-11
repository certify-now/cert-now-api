package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteProperty;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.ComplianceHealthDTO;
import com.uk.certifynow.certify_now.model.MyPropertiesResponse;
import com.uk.certifynow.certify_now.model.PropertyDTO;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.mappers.PropertyMapper;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class PropertyService {

  private final PropertyRepository propertyRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher publisher;
  private final PropertyMapper propertyMapper;
  private final ComplianceService complianceService;

  public PropertyService(
      final PropertyRepository propertyRepository,
      final UserRepository userRepository,
      final ApplicationEventPublisher publisher,
      final PropertyMapper propertyMapper,
      final ComplianceService complianceService) {
    this.propertyRepository = propertyRepository;
    this.userRepository = userRepository;
    this.publisher = publisher;
    this.propertyMapper = propertyMapper;
    this.complianceService = complianceService;
  }

  public List<PropertyDTO> findAll() {
    final List<Property> properties = propertyRepository.findAll(Sort.by("id"));
    return properties.stream()
        .map(p -> enriched(propertyMapper.toDTO(p)))
        .toList();
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
        .findByOwnerId(ownerId, pageable)
        .map(p -> enriched(propertyMapper.toDTO(p)));
  }

  /**
   * Returns all active properties for an owner together with the aggregate compliance health.
   * This is the primary endpoint for the properties list screen.
   */
  public MyPropertiesResponse getMyPropertiesWithCompliance(final UUID ownerId) {
    final List<PropertyDTO> properties =
        propertyRepository.findByOwnerIdAndIsActiveTrue(ownerId).stream()
            .map(p -> enriched(propertyMapper.toDTO(p)))
            .toList();
    final ComplianceHealthDTO health = complianceService.computeHealth(properties);
    return new MyPropertiesResponse(properties, health);
  }

  @Transactional
  public PropertyDTO create(final PropertyDTO propertyDTO) {
    final Property property = new Property();
    propertyMapper.updateEntity(propertyDTO, property);
    // Resolve owner reference from UUID
    final User owner =
        propertyDTO.getOwner() == null
            ? null
            : userRepository
                .findById(propertyDTO.getOwner())
                .orElseThrow(() -> new NotFoundException("owner not found"));
    property.setOwner(owner);
    if (property.getId() == null) {
      final java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
      property.setCreatedAt(now);
      property.setUpdatedAt(now);
    }
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
  public void update(final UUID id, final PropertyDTO propertyDTO) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    final java.time.OffsetDateTime createdAt = property.getCreatedAt();
    propertyMapper.updateEntity(propertyDTO, property);
    // Resolve owner reference from UUID
    final User owner =
        propertyDTO.getOwner() == null
            ? null
            : userRepository
                .findById(propertyDTO.getOwner())
                .orElseThrow(() -> new NotFoundException("owner not found"));
    property.setOwner(owner);
    property.setCreatedAt(createdAt);
    property.setUpdatedAt(java.time.OffsetDateTime.now());
    propertyRepository.save(property);
    log.info("Property {} updated", id);
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

  /**
   * Upload or update the Gas Safety Certificate PDF and/or expiry metadata for a property.
   * The landlord may supply only metadata (no file) if they declared cert info during registration
   * and just want to upload the PDF now.
   */
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
        property.setGasCertPdfBytes(pdfFile.getBytes());
        property.setGasCertPdfName(pdfFile.getOriginalFilename());
      } catch (java.io.IOException e) {
        throw new RuntimeException("Failed to read gas cert PDF", e);
      }
    }
    property.setUpdatedAt(java.time.OffsetDateTime.now());
    final Property saved = propertyRepository.save(property);
    log.info("Gas certificate updated for property {}", id);
    return enriched(propertyMapper.toDTO(saved));
  }

  /**
   * Upload or update the EICR PDF and/or expiry metadata for a property.
   */
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
        property.setEicrCertPdfBytes(pdfFile.getBytes());
        property.setEicrCertPdfName(pdfFile.getOriginalFilename());
      } catch (java.io.IOException e) {
        throw new RuntimeException("Failed to read EICR PDF", e);
      }
    }
    property.setUpdatedAt(java.time.OffsetDateTime.now());
    final Property saved = propertyRepository.save(property);
    log.info("EICR certificate updated for property {}", id);
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

  private PropertyDTO enriched(final PropertyDTO dto) {
    complianceService.enrich(dto);
    return dto;
  }
}
