package com.uk.certifynow.certify_now.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.model.PropertyDTO;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.auth.AuthProvider;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import com.uk.certifynow.certify_now.service.auth.UserStatus;
import com.uk.certifynow.certify_now.service.mappers.PropertyMapper;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("PropertyService")
class PropertyServiceTest {

  @Mock private PropertyRepository propertyRepository;
  @Mock private UserRepository userRepository;
  @Mock private ApplicationEventPublisher publisher;
  @Mock private PropertyMapper propertyMapper;
  @Mock private ComplianceService complianceService;

  @InjectMocks private PropertyService service;

  private User owner;
  private PropertyDTO inputDTO;
  private Property mappedProperty;
  private PropertyDTO outputDTO;

  @BeforeEach
  void setUp() {
    owner = new User();
    owner.setId(UUID.randomUUID());
    owner.setEmail("owner@example.com");
    owner.setFullName("Property Owner");
    owner.setEmailVerified(true);
    owner.setStatus(UserStatus.ACTIVE);
    owner.setRole(UserRole.CUSTOMER);
    owner.setAuthProvider(AuthProvider.EMAIL);
    owner.setPasswordHash("$2a$12$dummyHash");
    owner.setCreatedAt(OffsetDateTime.now());
    owner.setUpdatedAt(OffsetDateTime.now());

    inputDTO = new PropertyDTO();
    inputDTO.setOwner(owner.getId());
    inputDTO.setAddressLine1("10 Test Street");
    inputDTO.setCity("London");
    inputDTO.setPostcode("SW1A 1AA");
    inputDTO.setCountry("GB");

    mappedProperty = new Property();
    mappedProperty.setId(UUID.randomUUID());

    outputDTO = new PropertyDTO();
    outputDTO.setId(mappedProperty.getId());
    outputDTO.setAddressLine1("10 Test Street");
  }

  // ══════════════════════════════════════════════════════════════════════
  // create()
  // ══════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("create()")
  class CreateTests {

    @Test
    @DisplayName("Sets isActive = true on every new property")
    void setsIsActiveTrue() {
      when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
      when(propertyRepository.save(any(Property.class)))
          .thenAnswer(
              inv -> {
                final Property p = inv.getArgument(0);
                p.setOwner(owner);
                return p;
              });
      when(propertyMapper.toDTO(any(Property.class))).thenReturn(outputDTO);

      service.create(inputDTO, null, null);

      final ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
      verify(propertyRepository).save(captor.capture());
      assertThat(captor.getValue().getIsActive()).isTrue();
    }

    @Test
    @DisplayName("Populates createdAt and updatedAt on a new property")
    void populatesTimestamps() {
      when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
      when(propertyRepository.save(any(Property.class)))
          .thenAnswer(
              inv -> {
                final Property p = inv.getArgument(0);
                p.setOwner(owner);
                return p;
              });
      when(propertyMapper.toDTO(any(Property.class))).thenReturn(outputDTO);

      service.create(inputDTO, null, null);

      final ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
      verify(propertyRepository).save(captor.capture());
      assertThat(captor.getValue().getCreatedAt()).isNotNull();
      assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Resolves and sets the owner from the DTO's owner UUID")
    void resolvesOwner() {
      when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
      when(propertyRepository.save(any(Property.class)))
          .thenAnswer(
              inv -> {
                final Property p = inv.getArgument(0);
                p.setOwner(owner);
                return p;
              });
      when(propertyMapper.toDTO(any(Property.class))).thenReturn(outputDTO);

      service.create(inputDTO, null, null);

      final ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
      verify(propertyRepository).save(captor.capture());
      assertThat(captor.getValue().getOwner()).isEqualTo(owner);
    }

    @Test
    @DisplayName("Publishes PropertyCreatedEvent after successful save")
    void publishesCreatedEvent() {
      when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
      when(propertyRepository.save(any(Property.class)))
          .thenAnswer(
              inv -> {
                final Property p = inv.getArgument(0);
                p.setOwner(owner);
                return p;
              });
      when(propertyMapper.toDTO(any(Property.class))).thenReturn(outputDTO);

      service.create(inputDTO, null, null);

      verify(publisher)
          .publishEvent(any(com.uk.certifynow.certify_now.events.PropertyCreatedEvent.class));
    }

    @Test
    @DisplayName("Throws NotFoundException when owner UUID does not exist")
    void throwsWhenOwnerNotFound() {
      when(userRepository.findById(owner.getId())).thenReturn(Optional.empty());

      org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create(inputDTO, null, null))
          .isInstanceOf(com.uk.certifynow.certify_now.util.NotFoundException.class);
    }
  }

  // ══════════════════════════════════════════════════════════════════════
  // deactivate()
  // ══════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("deactivate()")
  class DeactivateTests {

    @Test
    @DisplayName("Sets isActive = false and saves")
    void setsIsActiveFalse() {
      final Property active = new Property();
      active.setId(UUID.randomUUID());
      active.setIsActive(true);

      when(propertyRepository.findById(active.getId())).thenReturn(Optional.of(active));
      when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

      service.deactivate(active.getId());

      final ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
      verify(propertyRepository).save(captor.capture());
      assertThat(captor.getValue().getIsActive()).isFalse();
    }
  }
}
