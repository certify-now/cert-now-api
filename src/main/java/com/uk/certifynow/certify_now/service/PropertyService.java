package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteProperty;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.PropertyDTO;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.rest.dto.property.ComplianceHealth;
import com.uk.certifynow.certify_now.rest.dto.property.MyPropertiesResponse;
import com.uk.certifynow.certify_now.service.mappers.PropertyMapper;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.io.IOException;
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
  private final ComplianceHealthService complianceHealthService;

  public PropertyService(
      final PropertyRepository propertyRepository,
      final UserRepository userRepository,
      final ApplicationEventPublisher publisher,
      final PropertyMapper propertyMapper,
      final ComplianceHealthService complianceHealthService) {
    this.propertyRepository = propertyRepository;
    this.userRepository = userRepository;
    this.publisher = publisher;
    this.propertyMapper = propertyMapper;
    this.complianceHealthService = complianceHealthService;
  }

  public List<PropertyDTO> findAll() {
    final List<Property> properties = propertyRepository.findAll(Sort.by("id"));
    return properties.stream().map(propertyMapper::toDTO).toList();
  }

  public PropertyDTO get(final UUID id) {
    return propertyRepository
        .findById(id)
        .map(propertyMapper::toDTO)
        .orElseThrow(NotFoundException::new);
  }

  public org.springframework.data.domain.Page<PropertyDTO> getByOwner(
      final UUID ownerId, final org.springframework.data.domain.Pageable pageable) {
    return propertyRepository.findByOwnerId(ownerId, pageable).map(propertyMapper::toDTO);
  }

  @Transactional
  public PropertyDTO create(
      final PropertyDTO propertyDTO,
      final MultipartFile gasCertPdf,
      final MultipartFile eicrCertPdf) {
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
  public void update(
      final UUID id,
      final PropertyDTO propertyDTO,
      final MultipartFile gasCertPdf,
      final MultipartFile eicrCertPdf) {
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
    attachPdfs(property, gasCertPdf, eicrCertPdf);
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
   * Returns the full MyPropertiesResponse including compliance health for the home screen.
   *
   * @param ownerId UUID of the property owner
   * @return MyPropertiesResponse with property list and compliance health
   */
  public MyPropertiesResponse getMyPropertiesWithCompliance(final UUID ownerId) {
    final List<Property> properties =
        propertyRepository.findByOwnerIdAndIsActiveTrue(ownerId, Sort.by("createdAt").descending());
    final List<PropertyDTO> propertyDTOs = properties.stream().map(propertyMapper::toDTO).toList();
    final ComplianceHealth complianceHealth = complianceHealthService.calculate(properties);
    return new MyPropertiesResponse(propertyDTOs, complianceHealth);
  }

  /**
   * Retrieves the raw gas certificate PDF bytes for the given property.
   *
   * @param id property UUID
   * @return the PDF bytes
   */
  public byte[] getGasCertPdf(final UUID id) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    if (property.getGasCertPdf() == null || property.getGasCertPdf().length == 0) {
      throw new NotFoundException("No gas certificate PDF found");
    }
    return property.getGasCertPdf();
  }

  /**
   * Retrieves the raw EICR certificate PDF bytes for the given property.
   *
   * @param id property UUID
   * @return the PDF bytes
   */
  public byte[] getEicrCertPdf(final UUID id) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    if (property.getEicrCertPdf() == null || property.getEicrCertPdf().length == 0) {
      throw new NotFoundException("No EICR certificate PDF found");
    }
    return property.getEicrCertPdf();
  }

  // ── PDF attachment helper ──────────────────────────────────────────────────

  private void attachPdfs(
      final Property property, final MultipartFile gasCertPdf, final MultipartFile eicrCertPdf) {
    try {
      if (gasCertPdf != null && !gasCertPdf.isEmpty()) {
        property.setGasCertPdf(gasCertPdf.getBytes());
        property.setGasCertPdfName(gasCertPdf.getOriginalFilename());
      }
      if (eicrCertPdf != null && !eicrCertPdf.isEmpty()) {
        property.setEicrCertPdf(eicrCertPdf.getBytes());
        property.setEicrCertPdfName(eicrCertPdf.getOriginalFilename());
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to read uploaded PDF", e);
    }
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
}
