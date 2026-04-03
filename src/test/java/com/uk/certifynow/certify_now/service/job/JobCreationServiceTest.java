package com.uk.certifynow.certify_now.service.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.domain.enums.CertificateType;
import com.uk.certifynow.certify_now.events.job.JobCreatedEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.interfaces.PricingCalculator;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.PaymentRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.rest.dto.job.CreateJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.JobResponse;
import com.uk.certifynow.certify_now.rest.dto.pricing.PriceBreakdown;
import com.uk.certifynow.certify_now.service.enums.JobStatus;
import com.uk.certifynow.certify_now.util.TestConstants;
import com.uk.certifynow.certify_now.util.TestPropertyBuilder;
import com.uk.certifynow.certify_now.util.TestUserBuilder;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class JobCreationServiceTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  @Mock private JobRepository jobRepository;
  @Mock private UserRepository userRepository;
  @Mock private PropertyRepository propertyRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private PricingCalculator pricingCalculator;
  @Mock private ReferenceNumberGenerator referenceNumberGenerator;
  @Mock private ApplicationEventPublisher publisher;
  @Mock private JobHistoryService jobHistoryService;

  private JobCreationService jobCreationService;
  private JobResponseMapper jobResponseMapper;

  @BeforeEach
  void setUp() {
    jobResponseMapper = new JobResponseMapper(new ObjectMapper());
    jobCreationService =
        new JobCreationService(
            jobRepository,
            userRepository,
            propertyRepository,
            paymentRepository,
            pricingCalculator,
            referenceNumberGenerator,
            publisher,
            new ObjectMapper(),
            clock,
            jobResponseMapper,
            jobHistoryService);
  }

  // --- CREATE JOB ----------------------------------------------------------------

  @Test
  void createJob_happyPath_returnsCreatedJob() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final PriceBreakdown price = buildPrice();

    when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
    when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));
    when(pricingCalculator.calculate(
            CertificateType.GAS_SAFETY.name(), property.getId(), "STANDARD"))
        .thenReturn(price);
    when(referenceNumberGenerator.generate()).thenReturn("CN-00000001");
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    final CreateJobRequest req =
        new CreateJobRequest(
            property.getId(), CertificateType.GAS_SAFETY.name(), "STANDARD", null, null, null);

    final JobResponse response = jobCreationService.createJob(customer.getId(), req);

    assertThat(response).isNotNull();
    assertThat(response.status()).isEqualTo(JobStatus.CREATED.name());
    assertThat(response.certificateType()).isEqualTo(CertificateType.GAS_SAFETY.name());

    final ArgumentCaptor<JobCreatedEvent> eventCaptor =
        ArgumentCaptor.forClass(JobCreatedEvent.class);
    verify(publisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getCustomerId()).isEqualTo(customer.getId());
  }

  @Test
  void createJob_propertyNotOwned_throwsAccessDenied() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User otherCustomer =
        TestUserBuilder.buildActiveCustomer(UUID.randomUUID(), "other@example.com");
    final Property property = TestPropertyBuilder.buildWithGas(otherCustomer);

    when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
    when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));

    final CreateJobRequest req =
        new CreateJobRequest(
            property.getId(), CertificateType.GAS_SAFETY.name(), null, null, null, null);

    assertThatThrownBy(() -> jobCreationService.createJob(customer.getId(), req))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void createJob_inactiveProperty_throwsBadRequest() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildInactive(customer);

    when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
    when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));

    final CreateJobRequest req =
        new CreateJobRequest(
            property.getId(), CertificateType.GAS_SAFETY.name(), null, null, null, null);

    assertThatThrownBy(() -> jobCreationService.createJob(customer.getId(), req))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("inactive");
  }

  @Test
  void createJob_gasSafety_noGasSupply_throwsBadRequest() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithElectric(customer);

    when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
    when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));

    final CreateJobRequest req =
        new CreateJobRequest(
            property.getId(), CertificateType.GAS_SAFETY.name(), null, null, null, null);

    assertThatThrownBy(() -> jobCreationService.createJob(customer.getId(), req))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("gas supply");
  }

  @Test
  void createJob_invalidPreferredDay_throwsBadRequest() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);

    when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
    when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));

    final var badDay =
        new com.uk.certifynow.certify_now.rest.dto.job.DayAvailability(
            "FUNDAY", List.of("MORNING"));
    final CreateJobRequest req =
        new CreateJobRequest(
            property.getId(), CertificateType.GAS_SAFETY.name(), null, null, null, List.of(badDay));

    assertThatThrownBy(() -> jobCreationService.createJob(customer.getId(), req))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e ->
                assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo("INVALID_PREFERRED_DAY"));
  }

  @Test
  void createJob_invalidPreferredTimeSlot_throwsBadRequest() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);

    when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
    when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));

    final var badSlot =
        new com.uk.certifynow.certify_now.rest.dto.job.DayAvailability("MON", List.of("MIDNIGHT"));
    final CreateJobRequest req =
        new CreateJobRequest(
            property.getId(),
            CertificateType.GAS_SAFETY.name(),
            null,
            null,
            null,
            List.of(badSlot));

    assertThatThrownBy(() -> jobCreationService.createJob(customer.getId(), req))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e ->
                assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo("INVALID_PREFERRED_TIME_SLOT"));
  }

  @Test
  void createJob_referenceNumberCollision_retriesUpTo3Times() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final PriceBreakdown price = buildPrice();

    when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
    when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));
    when(pricingCalculator.calculate(anyString(), any(), anyString())).thenReturn(price);
    when(referenceNumberGenerator.generate()).thenReturn("REF");
    // First two attempts throw; third succeeds
    when(jobRepository.save(any()))
        .thenThrow(new DataIntegrityViolationException("dup"))
        .thenThrow(new DataIntegrityViolationException("dup"))
        .thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    final CreateJobRequest req =
        new CreateJobRequest(
            property.getId(), CertificateType.GAS_SAFETY.name(), null, null, null, null);

    final JobResponse response = jobCreationService.createJob(customer.getId(), req);

    assertThat(response).isNotNull();
    verify(jobRepository, times(3)).save(any());
  }

  // --- Helpers -------------------------------------------------------------------

  private PriceBreakdown buildPrice() {
    return new PriceBreakdown(
        9900,
        0,
        0,
        0,
        9900,
        new BigDecimal("0.200"),
        1980,
        7920,
        new PriceBreakdown.Breakdown(List.of(), "STANDARD", BigDecimal.ONE));
  }
}
