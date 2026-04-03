package com.uk.certifynow.certify_now.service.job;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Payment;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.domain.enums.CertificateType;
import com.uk.certifynow.certify_now.events.job.JobCreatedEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.interfaces.PricingCalculator;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.PaymentRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.rest.dto.job.CreateJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.DayAvailability;
import com.uk.certifynow.certify_now.rest.dto.job.JobResponse;
import com.uk.certifynow.certify_now.rest.dto.pricing.PriceBreakdown;
import com.uk.certifynow.certify_now.service.enums.ActorType;
import com.uk.certifynow.certify_now.service.enums.JobStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class JobCreationService {

  private final JobRepository jobRepository;
  private final UserRepository userRepository;
  private final PropertyRepository propertyRepository;
  private final PaymentRepository paymentRepository;
  private final PricingCalculator pricingCalculator;
  private final ReferenceNumberGenerator referenceNumberGenerator;
  private final ApplicationEventPublisher publisher;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final JobResponseMapper jobResponseMapper;
  private final JobHistoryService jobHistoryService;

  public JobCreationService(
      final JobRepository jobRepository,
      final UserRepository userRepository,
      final PropertyRepository propertyRepository,
      final PaymentRepository paymentRepository,
      final PricingCalculator pricingCalculator,
      final ReferenceNumberGenerator referenceNumberGenerator,
      final ApplicationEventPublisher publisher,
      final ObjectMapper objectMapper,
      final Clock clock,
      final JobResponseMapper jobResponseMapper,
      final JobHistoryService jobHistoryService) {
    this.jobRepository = jobRepository;
    this.userRepository = userRepository;
    this.propertyRepository = propertyRepository;
    this.paymentRepository = paymentRepository;
    this.pricingCalculator = pricingCalculator;
    this.referenceNumberGenerator = referenceNumberGenerator;
    this.publisher = publisher;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.jobResponseMapper = jobResponseMapper;
    this.jobHistoryService = jobHistoryService;
  }

  @Transactional
  public JobResponse createJob(final UUID customerId, final CreateJobRequest request) {
    // 1. Load customer
    final User customer =
        userRepository
            .findById(customerId)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + customerId));

    // 2. Load property, validate ownership
    final Property property =
        propertyRepository
            .findById(request.propertyId())
            .orElseThrow(
                () -> new EntityNotFoundException("Property not found: " + request.propertyId()));
    if (!property.getOwner().getId().equals(customerId)) {
      throw new AccessDeniedException("Property does not belong to this customer");
    }
    if (!Boolean.TRUE.equals(property.getIsActive())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PROPERTY_INACTIVE",
          "This property is inactive and cannot be booked");
    }

    // 3. Business validation: gas safety requires gas supply
    final String certType = request.certificateType();
    if (CertificateType.GAS_SAFETY.name().equals(certType)
        && !Boolean.TRUE.equals(property.getHasGasSupply())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "NO_GAS_SUPPLY",
          "Property does not have a gas supply; cannot book a gas safety certificate");
    }

    // Validate preferred availability if provided
    if (request.preferredAvailability() != null && !request.preferredAvailability().isEmpty()) {
      final Set<String> validDays = Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
      final Set<String> validSlots = Set.of("MORNING", "AFTERNOON", "EVENING");
      for (final DayAvailability entry : request.preferredAvailability()) {
        if (!validDays.contains(entry.day().toUpperCase())) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST,
              "INVALID_PREFERRED_DAY",
              "Invalid preferred day: " + entry.day() + ". Must be MON-SUN.");
        }
        for (final String slot : entry.slots()) {
          if (!validSlots.contains(slot.toUpperCase())) {
            throw new BusinessException(
                HttpStatus.BAD_REQUEST,
                "INVALID_PREFERRED_TIME_SLOT",
                "Invalid preferred time slot: "
                    + slot
                    + ". Must be MORNING, AFTERNOON, or EVENING.");
          }
        }
      }
    }

    // 4. Calculate pricing
    final String urgency = request.urgencyOrDefault();
    final PriceBreakdown price = pricingCalculator.calculate(certType, property.getId(), urgency);

    // 5. Generate reference number (retry up to 3 times on collision)
    Job saved = null;
    for (int attempt = 0; attempt < 3; attempt++) {
      final Job job = buildJob(customer, property, request, certType, urgency, price);
      try {
        saved = jobRepository.save(job);
        break;
      } catch (final DataIntegrityViolationException e) {
        if (attempt == 2) throw e; // give up after 3 attempts
      }
    }

    // 6. Record initial status history (null -> CREATED)
    jobHistoryService.recordHistory(
        saved, null, JobStatus.CREATED.name(), customerId, ActorType.CUSTOMER.name(), null, null);

    // 7. Create stub payment
    final Payment payment = buildPayment(customer, saved, price);
    final Payment savedPayment = paymentRepository.save(payment);

    // 8. Publish event (fires after commit via @TransactionalEventListener)
    publisher.publishEvent(
        new JobCreatedEvent(
            saved.getId(), customerId, property.getId(), certType, price.totalPricePence()));

    return jobResponseMapper.toJobResponse(saved, savedPayment);
  }

  private Job buildJob(
      final User customer,
      final Property property,
      final CreateJobRequest request,
      final String certType,
      final String urgency,
      final PriceBreakdown price) {
    final Job job = new Job();
    job.setCustomer(customer);
    job.setProperty(property);
    job.setCertificateType(certType);
    job.setUrgency(urgency);
    job.setStatus(JobStatus.CREATED.name());
    job.setReferenceNumber(referenceNumberGenerator.generate());
    job.setMatchAttempts(0);
    job.setAdminAlertCount(0);
    job.setAccessInstructions(request.accessInstructions());
    job.setCustomerNotes(request.customerNotes());
    if (request.preferredAvailability() != null && !request.preferredAvailability().isEmpty()) {
      try {
        job.setPreferredAvailability(
            objectMapper.writeValueAsString(request.preferredAvailability()));
      } catch (final JacksonException e) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "INVALID_AVAILABILITY",
            "Failed to serialise availability preferences");
      }
    }
    // Pricing -- copied from breakdown at creation time
    job.setBasePricePence(price.basePricePence());
    job.setPropertyModifierPence(price.propertyModifierPence());
    job.setUrgencyModifierPence(price.urgencyModifierPence());
    job.setDiscountPence(price.discountPence());
    job.setTotalPricePence(price.totalPricePence());
    job.setCommissionRate(price.commissionRate());
    job.setCommissionPence(price.commissionPence());
    job.setEngineerPayoutPence(price.engineerPayoutPence());
    job.setCreatedAt(OffsetDateTime.now(clock));
    job.setUpdatedAt(OffsetDateTime.now(clock));
    return job;
  }

  private Payment buildPayment(final User customer, final Job job, final PriceBreakdown price) {
    final Payment payment = new Payment();
    payment.setCustomer(customer);
    payment.setJob(job);
    payment.setStatus("PENDING");
    payment.setAmountPence(price.totalPricePence());
    payment.setCurrency("GBP");
    payment.setRequiresAction(false);
    // Stub values -- real Stripe integration in Phase 8
    payment.setStripePaymentIntentId("stub_pi_" + job.getId());
    payment.setStripeClientSecret("stub_secret_" + job.getId());
    payment.setCreatedAt(OffsetDateTime.now(clock));
    payment.setUpdatedAt(OffsetDateTime.now(clock));
    return payment;
  }
}
