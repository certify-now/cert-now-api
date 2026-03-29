package com.uk.certifynow.certify_now.util;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.domain.enums.CertificateType;
import com.uk.certifynow.certify_now.service.job.JobStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class TestJobBuilder {

  private static final OffsetDateTime NOW = TestConstants.FIXED_NOW;

  private TestJobBuilder() {}

  public static Job buildCreated(final User customer, final Property property) {
    return base(customer, property, JobStatus.CREATED);
  }

  public static Job buildMatched(
      final User customer, final Property property, final User engineer) {
    final Job j = base(customer, property, JobStatus.MATCHED);
    j.setEngineer(engineer);
    j.setMatchedAt(NOW.minusHours(1));
    j.setMatchAttempts(1);
    return j;
  }

  public static Job buildAccepted(
      final User customer, final Property property, final User engineer) {
    final Job j = buildMatched(customer, property, engineer);
    j.setStatus(JobStatus.ACCEPTED.name());
    j.setScheduledDate(LocalDate.now(TestConstants.FIXED_CLOCK).plusDays(2));
    j.setScheduledTimeSlot("MORNING");
    j.setAcceptedAt(NOW.minusMinutes(30));
    return j;
  }

  public static Job buildEnRoute(
      final User customer, final Property property, final User engineer) {
    final Job j = buildAccepted(customer, property, engineer);
    j.setStatus(JobStatus.EN_ROUTE.name());
    j.setEnRouteAt(NOW.minusMinutes(15));
    return j;
  }

  public static Job buildInProgress(
      final User customer, final Property property, final User engineer) {
    final Job j = buildEnRoute(customer, property, engineer);
    j.setStatus(JobStatus.IN_PROGRESS.name());
    j.setStartedAt(NOW.minusMinutes(10));
    return j;
  }

  public static Job buildCompleted(
      final User customer, final Property property, final User engineer) {
    final Job j = buildInProgress(customer, property, engineer);
    j.setStatus(JobStatus.COMPLETED.name());
    j.setCompletedAt(NOW.minusMinutes(5));
    return j;
  }

  private static Job base(final User customer, final Property property, final JobStatus status) {
    final Job j = new Job();
    j.setId(UUID.randomUUID());
    j.setCustomer(customer);
    j.setProperty(property);
    j.setStatus(status.name());
    j.setCertificateType(CertificateType.GAS_SAFETY.name());
    j.setUrgency("STANDARD");
    j.setReferenceNumber("CN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    j.setMatchAttempts(0);
    j.setAdminAlertCount(0);
    j.setBasePricePence(9900);
    j.setPropertyModifierPence(0);
    j.setUrgencyModifierPence(0);
    j.setDiscountPence(0);
    j.setTotalPricePence(9900);
    j.setCommissionRate(new BigDecimal("0.200"));
    j.setCommissionPence(1980);
    j.setEngineerPayoutPence(7920);
    j.setCreatedAt(NOW.minusHours(2));
    j.setUpdatedAt(NOW);
    return j;
  }
}
