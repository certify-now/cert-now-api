package com.uk.certifynow.certify_now.service.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.JobMatchLog;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.job.JobMatchedEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.JobMatchLogRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.rest.dto.job.JobResponse;
import com.uk.certifynow.certify_now.service.auth.EngineerApplicationStatus;
import com.uk.certifynow.certify_now.service.auth.EngineerTier;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

  @Mock private JobRepository jobRepository;
  @Mock private EngineerProfileRepository engineerProfileRepository;
  @Mock private JobMatchLogRepository matchLogRepository;
  @Mock private UserRepository userRepository;
  @Mock private ApplicationEventPublisher publisher;

  @InjectMocks private MatchingService matchingService;

  // ── Shared test fixtures ─────────────────────────────────────────────────
  private UUID jobId;
  private UUID engineerId;
  private UUID customerId;
  private UUID propertyId;
  private User customer;
  private User engineer;
  private Property property;
  private Job job;
  private EngineerProfile engineerProfile;

  @BeforeEach
  void setUp() {
    jobId = UUID.randomUUID();
    engineerId = UUID.randomUUID();
    customerId = UUID.randomUUID();
    propertyId = UUID.randomUUID();

    customer = buildUser(customerId, UserRole.CUSTOMER);
    engineer = buildUser(engineerId, UserRole.ENGINEER);
    property = buildProperty(propertyId, customer);
    job = buildJob(jobId, "CREATED", customer, property, null);
    engineerProfile = buildEngineerProfile(engineer);
  }

  // ════════════════════════════════════════════════════════════════════════════
  // findCandidates()
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("findCandidates()")
  class FindCandidatesTests {

    @Test
    @DisplayName("Returns nearby approved engineers under daily cap")
    void returnsEligibleEngineers() {
      property.setLocation("POINT(-0.1278 51.5074)");

      when(engineerProfileRepository.findNearbyApproved(51.5074, -0.1278))
          .thenReturn(List.of(engineerProfile));
      when(jobRepository.countEngineerJobsToday(eq(engineerId), any(OffsetDateTime.class)))
          .thenReturn(0L);

      final List<EngineerProfile> candidates = matchingService.findCandidates(job);

      assertThat(candidates).hasSize(1).containsExactly(engineerProfile);
    }

    @Test
    @DisplayName("Filters out engineers exceeding daily job cap")
    void filtersEngineersOverCap() {
      property.setLocation("POINT(-0.1278 51.5074)");

      when(engineerProfileRepository.findNearbyApproved(51.5074, -0.1278))
          .thenReturn(List.of(engineerProfile));
      when(jobRepository.countEngineerJobsToday(eq(engineerId), any(OffsetDateTime.class)))
          .thenReturn(10L); // maxDailyJobs is 10

      final List<EngineerProfile> candidates = matchingService.findCandidates(job);

      assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("Falls back to all approved when property has no location")
    void fallsBackWhenNoLocation() {
      property.setLocation(null);

      when(engineerProfileRepository.findByStatus(EngineerApplicationStatus.APPROVED))
          .thenReturn(List.of(engineerProfile));

      final List<EngineerProfile> candidates = matchingService.findCandidates(job);

      assertThat(candidates).hasSize(1);
      verify(engineerProfileRepository).findByStatus(EngineerApplicationStatus.APPROVED);
    }

    @Test
    @DisplayName("Falls back to all approved when property has blank location")
    void fallsBackWhenBlankLocation() {
      property.setLocation("  ");

      when(engineerProfileRepository.findByStatus(EngineerApplicationStatus.APPROVED))
          .thenReturn(List.of(engineerProfile));

      final List<EngineerProfile> candidates = matchingService.findCandidates(job);

      assertThat(candidates).hasSize(1);
    }

    @Test
    @DisplayName("Returns multiple eligible engineers")
    void returnsMultipleEngineers() {
      property.setLocation("POINT(-0.1278 51.5074)");
      final EngineerProfile ep2 =
          buildEngineerProfile(buildUser(UUID.randomUUID(), UserRole.ENGINEER));

      when(engineerProfileRepository.findNearbyApproved(51.5074, -0.1278))
          .thenReturn(List.of(engineerProfile, ep2));
      when(jobRepository.countEngineerJobsToday(any(UUID.class), any(OffsetDateTime.class)))
          .thenReturn(0L);

      final List<EngineerProfile> candidates = matchingService.findCandidates(job);

      assertThat(candidates).hasSize(2);
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // broadcastToEligible()
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("broadcastToEligible()")
  class BroadcastTests {

    @Test
    @DisplayName("Happy path: sets AWAITING_ACCEPTANCE and creates match logs")
    void happyPath() {
      property.setLocation("POINT(-0.1278 51.5074)");

      when(engineerProfileRepository.findNearbyApproved(51.5074, -0.1278))
          .thenReturn(List.of(engineerProfile));
      when(jobRepository.countEngineerJobsToday(eq(engineerId), any(OffsetDateTime.class)))
          .thenReturn(0L);
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

      matchingService.broadcastToEligible(job);

      assertThat(job.getStatus()).isEqualTo("AWAITING_ACCEPTANCE");
      assertThat(job.getBroadcastAt()).isNotNull();
      verify(matchLogRepository).save(any(JobMatchLog.class));
    }

    @Test
    @DisplayName("Creates match log entries for every candidate")
    void createsMatchLogPerCandidate() {
      property.setLocation("POINT(-0.1278 51.5074)");
      final EngineerProfile ep2 =
          buildEngineerProfile(buildUser(UUID.randomUUID(), UserRole.ENGINEER));

      when(engineerProfileRepository.findNearbyApproved(51.5074, -0.1278))
          .thenReturn(List.of(engineerProfile, ep2));
      when(jobRepository.countEngineerJobsToday(any(UUID.class), any(OffsetDateTime.class)))
          .thenReturn(0L);
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

      matchingService.broadcastToEligible(job);

      verify(matchLogRepository, org.mockito.Mockito.times(2)).save(any(JobMatchLog.class));
    }

    @Test
    @DisplayName("Escalates immediately when no candidates found")
    void escalatesWhenNoCandidates() {
      property.setLocation("POINT(-0.1278 51.5074)");

      when(engineerProfileRepository.findNearbyApproved(51.5074, -0.1278)).thenReturn(List.of());
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

      matchingService.broadcastToEligible(job);

      assertThat(job.getStatus()).isEqualTo("ESCALATED");
    }

    @Test
    @DisplayName("Skips if job is not in CREATED status")
    void skipsNonCreatedStatus() {
      job.setStatus("MATCHED");

      matchingService.broadcastToEligible(job);

      verify(jobRepository, never()).save(any());
    }

    @Test
    @DisplayName("Skips if job is in AWAITING_ACCEPTANCE status")
    void skipsAwaitingAcceptance() {
      job.setStatus("AWAITING_ACCEPTANCE");

      matchingService.broadcastToEligible(job);

      verify(jobRepository, never()).save(any());
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // claimJob()
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("claimJob()")
  class ClaimJobTests {

    @Test
    @DisplayName("Happy path: first engineer claims successfully")
    void happyPath() {
      job.setStatus("AWAITING_ACCEPTANCE");
      final JobMatchLog matchLog = new JobMatchLog();
      matchLog.setJob(job);
      matchLog.setEngineer(engineer);
      matchLog.setResponse("PENDING");

      when(userRepository.findById(engineerId)).thenReturn(Optional.of(engineer));
      when(matchLogRepository.findByJobIdAndEngineerId(jobId, engineerId))
          .thenReturn(Optional.of(matchLog));
      when(jobRepository.claimJob(eq(jobId), eq(engineer), any(OffsetDateTime.class)))
          .thenReturn(1);
      when(matchLogRepository.save(any(JobMatchLog.class))).thenAnswer(inv -> inv.getArgument(0));
      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

      final JobResponse response = matchingService.claimJob(jobId, engineerId);

      assertThat(response).isNotNull();
      assertThat(response.id()).isEqualTo(jobId);
      verify(matchLogRepository).expireAllPendingForJob(jobId);
      verify(publisher).publishEvent(any(JobMatchedEvent.class));
    }

    @Test
    @DisplayName("Returns 409 when job already claimed by another engineer")
    void conflictWhenAlreadyClaimed() {
      final JobMatchLog matchLog = new JobMatchLog();
      matchLog.setJob(job);
      matchLog.setEngineer(engineer);

      when(userRepository.findById(engineerId)).thenReturn(Optional.of(engineer));
      when(matchLogRepository.findByJobIdAndEngineerId(jobId, engineerId))
          .thenReturn(Optional.of(matchLog));
      when(jobRepository.claimJob(eq(jobId), eq(engineer), any(OffsetDateTime.class)))
          .thenReturn(0); // 0 rows updated — already claimed

      assertThatThrownBy(() -> matchingService.claimJob(jobId, engineerId))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("already been claimed");
    }

    @Test
    @DisplayName("Fails if engineer not found")
    void engineerNotFound() {
      when(userRepository.findById(engineerId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> matchingService.claimJob(jobId, engineerId))
          .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Fails if user is not an engineer")
    void notAnEngineer() {
      when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));

      assertThatThrownBy(() -> matchingService.claimJob(jobId, customerId))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("not an engineer");
    }

    @Test
    @DisplayName("Fails if engineer was not notified about job")
    void notNotified() {
      when(userRepository.findById(engineerId)).thenReturn(Optional.of(engineer));
      when(matchLogRepository.findByJobIdAndEngineerId(jobId, engineerId))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> matchingService.claimJob(jobId, engineerId))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("not notified");
    }

    @Test
    @DisplayName("Updates match log to ACCEPTED on successful claim")
    void updatesMatchLogOnClaim() {
      job.setStatus("AWAITING_ACCEPTANCE");
      final JobMatchLog matchLog = new JobMatchLog();
      matchLog.setJob(job);
      matchLog.setEngineer(engineer);
      matchLog.setResponse("PENDING");

      when(userRepository.findById(engineerId)).thenReturn(Optional.of(engineer));
      when(matchLogRepository.findByJobIdAndEngineerId(jobId, engineerId))
          .thenReturn(Optional.of(matchLog));
      when(jobRepository.claimJob(eq(jobId), eq(engineer), any(OffsetDateTime.class)))
          .thenReturn(1);
      when(matchLogRepository.save(any(JobMatchLog.class))).thenAnswer(inv -> inv.getArgument(0));
      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

      matchingService.claimJob(jobId, engineerId);

      assertThat(matchLog.getResponse()).isEqualTo("ACCEPTED");
      assertThat(matchLog.getRespondedAt()).isNotNull();
      verify(matchLogRepository).save(matchLog);
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // escalateJob()
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("escalateJob()")
  class EscalateJobTests {

    @Test
    @DisplayName("Sets status to ESCALATED and expires pending match logs")
    void happyPath() {
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

      matchingService.escalateJob(job);

      assertThat(job.getStatus()).isEqualTo("ESCALATED");
      assertThat(job.getEscalatedAt()).isNotNull();
      verify(matchLogRepository).expireAllPendingForJob(jobId);
    }

    @Test
    @DisplayName("Sets updatedAt timestamp")
    void setsUpdatedAt() {
      final OffsetDateTime before = OffsetDateTime.now();
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

      matchingService.escalateJob(job);

      assertThat(job.getUpdatedAt()).isAfterOrEqualTo(before);
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // Helper builders
  // ════════════════════════════════════════════════════════════════════════════

  private User buildUser(final UUID id, final UserRole role) {
    final User user = new User();
    user.setId(id);
    user.setRole(role);
    user.setFullName("Test " + role.name());
    user.setEmail(id + "@test.com");
    user.setPasswordHash("hashed");
    user.setEmailVerified(true);
    user.setPhoneVerified(false);
    user.setCreatedAt(OffsetDateTime.now());
    user.setUpdatedAt(OffsetDateTime.now());
    return user;
  }

  private Property buildProperty(final UUID id, final User owner) {
    final Property prop = new Property();
    prop.setId(id);
    prop.setOwner(owner);
    prop.setIsActive(true);
    prop.setHasGasSupply(true);
    prop.setHasElectric(true);
    prop.setAddressLine1("1 Test Street");
    prop.setCity("London");
    prop.setPostcode("SW1A 1AA");
    prop.setCountry("GB");
    prop.setPropertyType("HOUSE");
    prop.setComplianceStatus("COMPLIANT");
    prop.setCreatedAt(OffsetDateTime.now());
    prop.setUpdatedAt(OffsetDateTime.now());
    return prop;
  }

  private Job buildJob(
      final UUID id, final String status, final User cust, final Property prop, final User eng) {
    final Job job = new Job();
    job.setId(id);
    job.setStatus(status);
    job.setCustomer(cust);
    job.setProperty(prop);
    job.setEngineer(eng);
    job.setCertificateType("GAS_SAFETY");
    job.setUrgency("STANDARD");
    job.setReferenceNumber("CN-TEST-REF");
    job.setMatchAttempts(0);
    job.setBasePricePence(8500);
    job.setPropertyModifierPence(0);
    job.setUrgencyModifierPence(0);
    job.setDiscountPence(0);
    job.setTotalPricePence(10000);
    job.setCommissionRate(new BigDecimal("0.150"));
    job.setCommissionPence(1500);
    job.setEngineerPayoutPence(8500);
    job.setCreatedAt(OffsetDateTime.now());
    job.setUpdatedAt(OffsetDateTime.now());
    return job;
  }

  private EngineerProfile buildEngineerProfile(final User user) {
    final EngineerProfile ep = new EngineerProfile();
    ep.setId(UUID.randomUUID());
    ep.setUser(user);
    ep.setStatus(EngineerApplicationStatus.APPROVED);
    ep.setMaxDailyJobs(10);
    ep.setServiceRadiusMiles(new BigDecimal("25.0"));
    ep.setAcceptanceRate(new BigDecimal("95.00"));
    ep.setAvgRating(new BigDecimal("4.50"));
    ep.setIsOnline(true);
    ep.setOnTimePercentage(new BigDecimal("98.00"));
    ep.setStripeOnboarded(true);
    ep.setTotalJobsCompleted(50);
    ep.setTotalReviews(40);
    ep.setTier(EngineerTier.GOLD);
    ep.setCreatedAt(OffsetDateTime.now());
    ep.setUpdatedAt(OffsetDateTime.now());
    return ep;
  }
}
