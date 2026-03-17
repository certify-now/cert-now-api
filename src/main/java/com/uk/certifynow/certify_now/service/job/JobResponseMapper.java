package com.uk.certifynow.certify_now.service.job;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Payment;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.rest.dto.job.DayAvailability;
import com.uk.certifynow.certify_now.rest.dto.job.JobResponse;
import com.uk.certifynow.certify_now.rest.dto.job.JobSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Stateless mapper that converts Job entities to response DTOs. Shared between JobService and
 * MatchingService to prevent divergence.
 */
@Component
public class JobResponseMapper {

  private final ObjectMapper objectMapper;

  public JobResponseMapper(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public JobResponse toJobResponse(final Job job, final Payment payment) {
    final Property prop = job.getProperty();
    final String propSummary =
        prop.getAddressLine1() + ", " + prop.getCity() + " " + prop.getPostcode();
    final User eng = job.getEngineer();
    final String engName = eng == null ? null : eng.getFullName();
    final UUID engId = eng == null ? null : eng.getId();

    final JobResponse.Pricing pricing =
        new JobResponse.Pricing(
            job.getBasePricePence(),
            job.getPropertyModifierPence(),
            job.getUrgencyModifierPence(),
            job.getDiscountPence(),
            job.getTotalPricePence(),
            job.getCommissionRate() == null ? 0.15 : job.getCommissionRate().doubleValue(),
            job.getCommissionPence(),
            job.getEngineerPayoutPence());

    final JobResponse.Payment paymentSummary =
        payment == null
            ? null
            : new JobResponse.Payment(
                payment.getId(),
                payment.getStatus(),
                payment.getStripeClientSecret(),
                payment.getAmountPence());

    final JobResponse.Timestamps timestamps =
        new JobResponse.Timestamps(
            job.getCreatedAt(),
            job.getMatchedAt(),
            job.getAcceptedAt(),
            job.getEnRouteAt(),
            job.getStartedAt(),
            job.getCompletedAt(),
            job.getCertifiedAt(),
            job.getCancelledAt());

    return new JobResponse(
        job.getId(),
        job.getReferenceNumber(),
        job.getCustomer().getId(),
        prop.getId(),
        propSummary,
        engId,
        engName,
        job.getCertificateType(),
        job.getStatus(),
        job.getUrgency(),
        job.getScheduledTimeSlot(),
        job.getScheduledDate(),
        job.getMatchAttempts(),
        job.getAccessInstructions(),
        job.getCustomerNotes(),
        parseAvailability(job.getPreferredAvailability()),
        job.getCancelledBy(),
        job.getCancellationReason(),
        pricing,
        paymentSummary,
        timestamps);
  }

  public JobSummaryResponse toJobSummary(final Job job) {
    final Property prop = job.getProperty();
    final String propSummary =
        prop == null
            ? null
            : prop.getAddressLine1() + ", " + prop.getCity() + " " + prop.getPostcode();
    final User eng = job.getEngineer();
    return new JobSummaryResponse(
        job.getId(),
        job.getReferenceNumber(),
        job.getCertificateType(),
        job.getStatus(),
        job.getUrgency(),
        job.getTotalPricePence(),
        job.getScheduledDate(),
        job.getScheduledTimeSlot(),
        propSummary,
        eng == null ? null : eng.getFullName(),
        job.getCreatedAt(),
        parseAvailability(job.getPreferredAvailability()));
  }

  public List<DayAvailability> parseAvailability(final String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return objectMapper.readValue(json, new TypeReference<List<DayAvailability>>() {});
    } catch (final JacksonException e) {
      return List.of();
    }
  }
}
