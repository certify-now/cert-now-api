// package com.uk.certifynow.certify_now.service;
//
// import static org.assertj.core.api.Assertions.assertThat;
// import static org.assertj.core.api.Assertions.assertThatThrownBy;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.eq;
// import static org.mockito.Mockito.verify;
// import static org.mockito.Mockito.when;
//
// import com.uk.certifynow.certify_now.domain.Property;
// import com.uk.certifynow.certify_now.domain.User;
// import com.uk.certifynow.certify_now.events.PropertySoftDeletedEvent;
// import com.uk.certifynow.certify_now.exception.BusinessException;
// import com.uk.certifynow.certify_now.repos.JobRepository;
// import com.uk.certifynow.certify_now.repos.PropertyRepository;
// import com.uk.certifynow.certify_now.repos.UserRepository;
// import com.uk.certifynow.certify_now.rest.dto.property.CreatePropertyRequest;
// import com.uk.certifynow.certify_now.service.certificate.ComplianceService;
// import com.uk.certifynow.certify_now.service.enums.PropertyType;
// import com.uk.certifynow.certify_now.service.mappers.PropertyMapper;
// import com.uk.certifynow.certify_now.service.property.AddressLookupService;
// import com.uk.certifynow.certify_now.service.property.PropertyService;
// import com.uk.certifynow.certify_now.util.TestConstants;
// import com.uk.certifynow.certify_now.util.TestPropertyBuilder;
// import com.uk.certifynow.certify_now.util.TestUserBuilder;
// import java.time.Clock;
// import java.time.OffsetDateTime;
// import java.util.Optional;
// import java.util.UUID;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;
// import org.springframework.context.ApplicationEventPublisher;
// import org.springframework.http.HttpStatus;
//
// @ExtendWith(MockitoExtension.class)
// class PropertyServiceTest {
//
//  private final Clock clock = TestConstants.FIXED_CLOCK;
//
//  @Mock private PropertyRepository propertyRepository;
//  @Mock private UserRepository userRepository;
//  @Mock private JobRepository jobRepository;
//  @Mock private ApplicationEventPublisher publisher;
//  @Mock private PropertyMapper propertyMapper;
//  @Mock private ComplianceService complianceService;
//  @Mock private AddressLookupService addressLookupService;
//
//  private PropertyService service;
//
//  @BeforeEach
//  void setUp() {
//    service =
//        new PropertyService(
//            propertyRepository,
//            userRepository,
//            jobRepository,
//            publisher,
//            propertyMapper,
//            complianceService,
//            addressLookupService,
//            clock);
//  }
//
//  // ─── create ───────────────────────────────────────────────────────────────────
//
//  @Test
//  void create_geocodingFails_throwsBusinessException() {
//    final User owner = TestUserBuilder.buildActiveCustomer();
//    final CreatePropertyRequest request =
//        new CreatePropertyRequest(
//            "10 Test Street",
//            null,
//            "London",
//            null,
//            "SW1A 1AA",
//            "GB",
//            PropertyType.FLAT,
//            true,
//            false,
//            2,
//            null,
//            null,
//            null,
//            null,
//            null,
//            null,
//            null);
//
//    when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
//    when(propertyRepository.existsByOwnerIdAndAddressLine1IgnoreCaseAndPostcodeIgnoreCase(
//            owner.getId(), "10 Test Street", "SW1A 1AA"))
//        .thenReturn(false);
//    when(propertyMapper.toEntity(request)).thenReturn(new Property());
//    // No coordinates in request AND centroid lookup returns null
//    when(addressLookupService.lookupPostcodeCentroid("SW1A 1AA")).thenReturn(null);
//
//    assertThatThrownBy(() -> service.create(request, owner.getId()))
//        .isInstanceOf(BusinessException.class)
//        .satisfies(
//            e -> {
//              final BusinessException ex = (BusinessException) e;
//              assertThat(ex.getErrorCode()).isEqualTo("GEOCODING_REQUIRED");
//              assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
//            });
//  }
//
//  @Test
//  void create_duplicateAddress_throwsConflict() {
//    final User owner = TestUserBuilder.buildActiveCustomer();
//    final CreatePropertyRequest request =
//        new CreatePropertyRequest(
//            "10 Test Street",
//            null,
//            "London",
//            null,
//            "SW1A 1AA",
//            "GB",
//            PropertyType.FLAT,
//            true,
//            false,
//            2,
//            null,
//            null,
//            null,
//            null,
//            null,
//            null,
//            null);
//
//    when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
//    when(propertyRepository.existsByOwnerIdAndAddressLine1IgnoreCaseAndPostcodeIgnoreCase(
//            owner.getId(), "10 Test Street", "SW1A 1AA"))
//        .thenReturn(true);
//
//    assertThatThrownBy(() -> service.create(request, owner.getId()))
//        .isInstanceOf(BusinessException.class)
//        .extracting(e -> ((BusinessException) e).getErrorCode())
//        .isEqualTo("DUPLICATE_PROPERTY");
//  }
//
//  // ─── softDelete ───────────────────────────────────────────────────────────────
//
//  @Test
//  void softDelete_withActiveJobs_throwsConflict() {
//    final User owner = TestUserBuilder.buildActiveCustomer();
//    final Property property = TestPropertyBuilder.buildWithGas(owner);
//
//    when(propertyRepository.findByIdIncludingDeleted(property.getId()))
//        .thenReturn(Optional.of(property));
//    when(jobRepository.existsByPropertyIdAndStatusNotIn(eq(property.getId()), any()))
//        .thenReturn(true);
//
//    assertThatThrownBy(() -> service.softDelete(property.getId(), owner.getId()))
//        .isInstanceOf(BusinessException.class)
//        .extracting(e -> ((BusinessException) e).getErrorCode())
//        .isEqualTo("ACTIVE_JOBS_EXIST");
//  }
//
//  @Test
//  void softDelete_noActiveJobs_setsDeletedAt_publishesEvent() {
//    final User owner = TestUserBuilder.buildActiveCustomer();
//    final Property property = TestPropertyBuilder.buildWithGas(owner);
//
//    when(propertyRepository.findByIdIncludingDeleted(property.getId()))
//        .thenReturn(Optional.of(property));
//    when(jobRepository.existsByPropertyIdAndStatusNotIn(eq(property.getId()), any()))
//        .thenReturn(false);
//    when(propertyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
//
//    service.softDelete(property.getId(), owner.getId());
//
//    assertThat(property.getDeletedAt()).isNotNull();
//    assertThat(property.getDeletedBy()).isEqualTo(owner.getId());
//    verify(publisher).publishEvent(any(PropertySoftDeletedEvent.class));
//  }
//
//  @Test
//  void softDelete_alreadyDeleted_throwsConflict() {
//    final User owner = TestUserBuilder.buildActiveCustomer();
//    final Property property = TestPropertyBuilder.buildWithGas(owner);
//    property.setDeletedAt(OffsetDateTime.now(clock).minusDays(1));
//
//    when(propertyRepository.findByIdIncludingDeleted(property.getId()))
//        .thenReturn(Optional.of(property));
//
//    assertThatThrownBy(() -> service.softDelete(property.getId(), owner.getId()))
//        .isInstanceOf(BusinessException.class)
//        .extracting(e -> ((BusinessException) e).getErrorCode())
//        .isEqualTo("ALREADY_DELETED");
//  }
//
//  // ─── restore ──────────────────────────────────────────────────────────────────
//
//  @Test
//  void restore_notDeleted_throwsConflict() {
//    final User owner = TestUserBuilder.buildActiveCustomer();
//    final Property property = TestPropertyBuilder.buildWithGas(owner);
//
//    when(propertyRepository.findByIdIncludingDeleted(property.getId()))
//        .thenReturn(Optional.of(property));
//
//    assertThatThrownBy(() -> service.restore(property.getId(), UUID.randomUUID()))
//        .isInstanceOf(BusinessException.class)
//        .extracting(e -> ((BusinessException) e).getErrorCode())
//        .isEqualTo("NOT_DELETED");
//  }
//
//  @Test
//  void restore_deletedProperty_clearsDeletedAt_publishesEvent() {
//    final User owner = TestUserBuilder.buildActiveCustomer();
//    final Property property = TestPropertyBuilder.buildWithGas(owner);
//    property.setDeletedAt(OffsetDateTime.now(clock).minusDays(1));
//    property.setDeletedBy(owner.getId());
//
//    when(propertyRepository.findByIdIncludingDeleted(property.getId()))
//        .thenReturn(Optional.of(property));
//    when(propertyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
//
//    service.restore(property.getId(), UUID.randomUUID());
//
//    assertThat(property.getDeletedAt()).isNull();
//    assertThat(property.getDeletedBy()).isNull();
//    verify(publisher).publishEvent(any(Object.class));
//  }
//
//  // ─── getForOwner ──────────────────────────────────────────────────────────────
//
//  @Test
//  void getForOwner_differentOwner_throwsAccessDenied() {
//    final User owner = TestUserBuilder.buildActiveCustomer();
//    final User other = TestUserBuilder.buildActiveCustomer(UUID.randomUUID(),
// "other@example.com");
//    final Property property = TestPropertyBuilder.buildWithGas(owner);
//
//    when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));
//
//    assertThatThrownBy(() -> service.getForOwner(property.getId(), other.getId()))
//        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
//  }
// }
