package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteProperty;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.PropertyDTO;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class PropertyService {

  private final PropertyRepository propertyRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher publisher;

  public PropertyService(
      final PropertyRepository propertyRepository,
      final UserRepository userRepository,
      final ApplicationEventPublisher publisher) {
    this.propertyRepository = propertyRepository;
    this.userRepository = userRepository;
    this.publisher = publisher;
  }

  public List<PropertyDTO> findAll() {
    final List<Property> properties = propertyRepository.findAll(Sort.by("id"));
    return properties.stream().map(property -> mapToDTO(property, new PropertyDTO())).toList();
  }

  public PropertyDTO get(final UUID id) {
    return propertyRepository
        .findById(id)
        .map(property -> mapToDTO(property, new PropertyDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public org.springframework.data.domain.Page<PropertyDTO> getByOwner(
      final UUID ownerId, final org.springframework.data.domain.Pageable pageable) {
    return propertyRepository
        .findByOwnerId(ownerId, pageable)
        .map(property -> mapToDTO(property, new PropertyDTO()));
  }

  public PropertyDTO create(final PropertyDTO propertyDTO) {
    final Property property = new Property();
    mapToEntity(propertyDTO, property);
    Property saved = propertyRepository.save(property);
    if (saved.getOwner() != null) {
      publisher.publishEvent(
          new com.uk.certifynow.certify_now.events.PropertyCreatedEvent(
              saved.getId(), saved.getOwner().getId()));
    }
    return mapToDTO(saved, new PropertyDTO());
  }

  public void update(final UUID id, final PropertyDTO propertyDTO) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    final java.time.OffsetDateTime createdAt = property.getCreatedAt();
    mapToEntity(propertyDTO, property);
    property.setCreatedAt(createdAt);
    property.setUpdatedAt(java.time.OffsetDateTime.now());
    propertyRepository.save(property);
  }

  public void deactivate(final UUID id) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    property.setIsActive(false);
    propertyRepository.save(property);
  }

  public void delete(final UUID id) {
    final Property property = propertyRepository.findById(id).orElseThrow(NotFoundException::new);
    publisher.publishEvent(new BeforeDeleteProperty(id));
    propertyRepository.delete(property);
  }

  private PropertyDTO mapToDTO(final Property property, final PropertyDTO propertyDTO) {
    propertyDTO.setId(property.getId());
    propertyDTO.setBedrooms(property.getBedrooms());
    propertyDTO.setCountry(property.getCountry());
    propertyDTO.setFloorAreaSqm(property.getFloorAreaSqm());
    propertyDTO.setFloors(property.getFloors());
    propertyDTO.setGasApplianceCount(property.getGasApplianceCount());
    propertyDTO.setHasElectric(property.getHasElectric());
    propertyDTO.setHasGasSupply(property.getHasGasSupply());
    propertyDTO.setIsActive(property.getIsActive());
    propertyDTO.setYearBuilt(property.getYearBuilt());
    propertyDTO.setCreatedAt(property.getCreatedAt());
    propertyDTO.setUpdatedAt(property.getUpdatedAt());
    propertyDTO.setPostcode(property.getPostcode());
    propertyDTO.setUprn(property.getUprn());
    propertyDTO.setEpcRegisterRef(property.getEpcRegisterRef());
    propertyDTO.setCity(property.getCity());
    propertyDTO.setCounty(property.getCounty());
    propertyDTO.setAddressLine1(property.getAddressLine1());
    propertyDTO.setAddressLine2(property.getAddressLine2());
    propertyDTO.setPropertyType(property.getPropertyType());
    propertyDTO.setComplianceStatus(property.getComplianceStatus());
    propertyDTO.setLocation(property.getLocation());
    propertyDTO.setOwner(property.getOwner() == null ? null : property.getOwner().getId());
    return propertyDTO;
  }

  private Property mapToEntity(final PropertyDTO propertyDTO, final Property property) {
    property.setBedrooms(propertyDTO.getBedrooms());
    property.setCountry(propertyDTO.getCountry());
    property.setFloorAreaSqm(propertyDTO.getFloorAreaSqm());
    property.setFloors(propertyDTO.getFloors());
    property.setGasApplianceCount(propertyDTO.getGasApplianceCount());
    property.setHasElectric(propertyDTO.getHasElectric());
    property.setHasGasSupply(propertyDTO.getHasGasSupply());
    property.setIsActive(propertyDTO.getIsActive());
    property.setYearBuilt(propertyDTO.getYearBuilt());
    if (property.getId() == null) {
      final java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
      property.setCreatedAt(now);
      property.setUpdatedAt(now);
    }
    property.setPostcode(propertyDTO.getPostcode());
    property.setUprn(propertyDTO.getUprn());
    property.setEpcRegisterRef(propertyDTO.getEpcRegisterRef());
    property.setCity(propertyDTO.getCity());
    property.setCounty(propertyDTO.getCounty());
    property.setAddressLine1(propertyDTO.getAddressLine1());
    property.setAddressLine2(propertyDTO.getAddressLine2());
    property.setPropertyType(propertyDTO.getPropertyType());
    if (propertyDTO.getComplianceStatus() == null
        || propertyDTO.getComplianceStatus().trim().isEmpty()) {
      property.setComplianceStatus("PENDING");
    } else {
      property.setComplianceStatus(propertyDTO.getComplianceStatus());
    }
    property.setLocation(propertyDTO.getLocation());
    final User owner =
        propertyDTO.getOwner() == null
            ? null
            : userRepository
                .findById(propertyDTO.getOwner())
                .orElseThrow(() -> new NotFoundException("owner not found"));
    property.setOwner(owner);
    return property;
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
