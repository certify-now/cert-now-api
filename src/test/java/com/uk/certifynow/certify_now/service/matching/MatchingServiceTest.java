package com.uk.certifynow.certify_now.service.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.JobMatchLog;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.domain.enums.CertificateType;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.JobMatchLogRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.enums.EngineerApplicationStatus;
import com.uk.certifynow.certify_now.service.enums.JobStatus;
import com.uk.certifynow.certify_now.service.enums.MatchLogResponse;
import com.uk.certifynow.certify_now.service.job.JobResponseMapper;
import com.uk.certifynow.certify_now.service.notification.AdminAlertService;
import com.uk.certifynow.certify_now.util.TestConstants;
import com.uk.certifynow.certify_now.util.TestJobBuilder;
import com.uk.certifynow.certify_now.util.TestPropertyBuilder;
import com.uk.certifynow.certify_now.util.TestUserBuilder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  @Mock private JobRepository jobRepository;
  @Mock private EngineerProfileRepository engineerProfileRepository;
  @Mock private JobMatchLogRepository matchLogRepository;
  @Mock private UserRepository userRepository;
  @Mock private ApplicationEventPublisher publisher;
  @Mock private JobResponseMapper jobResponseMapper;
  @Mock private AdminAlertService adminAlertService;

  private MatchingService matchingService;

  @BeforeEach
  void setUp() {
    matchingService =
        new MatchingService(
            jobRepository,
            engineerProfileRepository,
            matchLogRepository,
            userRepository,
            publisher,
            clock,
            jobResponseMapper,
            adminAlertService);
  }

  // ─── findCandidates ───────────────────────────────────────────────────────────

  @Test
  void findCandidates_returnsNearbyApprovedEngineers() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    property.setCoordinates(makePoint(-0.1278, 51.5074));
    final Job job = TestJobBuilder.buildCreated(customer, property);

    final User engineerUser = TestUserBuilder.buildActiveEngineer();
    final EngineerProfile ep = buildEngineerProfile(engineerUser, 5);

    when(engineerProfileRepository.findNearbyApproved(anyDouble(), anyDouble()))
        .thenReturn(List.of(ep));
    when(jobRepository.countEngineerJobsTodayBatch(any(), any())).thenReturn(List.of());

    final List<EngineerProfile> candidates = matchingService.findCandidates(job);

    assertThat(candidates).hasSize(1);
  }

  @Test
  void findCandidates_excludesEngineersOverDailyJobCap() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    property.setCoordinates(makePoint(-0.1278, 51.5074));
    final Job job = TestJobBuilder.buildCreated(customer, property);

    final User engineerUser = TestUserBuilder.buildActiveEngineer();
    final EngineerProfile ep = buildEngineerProfile(engineerUser, 3);

    when(engineerProfileRepository.findNearbyApproved(anyDouble(), anyDouble()))
        .thenReturn(List.of(ep));
    // Engineer already at max (3 jobs today)
    when(jobRepository.countEngineerJobsTodayBatch(any(), any()))
        .thenReturn(List.<Object[]>of(new Object[] {engineerUser.getId(), 3L}));

    final List<EngineerProfile> candidates = matchingService.findCandidates(job);

    assertThat(candidates).isEmpty();
  }

  @Test
  void findCandidates_propertyNoLocation_throwsBusinessException() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);

    assertThatThrownBy(() -> matchingService.findCandidates(job))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e -> {
              final BusinessException ex = (BusinessException) e;
              assertThat(ex.getErrorCode()).isEqualTo("GEOCODING_REQUIRED");
              assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            });
    verify(engineerProfileRepository, never()).findNearbyApproved(anyDouble(), anyDouble());
  }

  // ─── broadcastToEligible ──────────────────────────────────────────────────────

  @Test
  void broadcastToEligible_noCandidates_escalatesImmediately() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    property.setCoordinates(makePoint(-0.1278, 51.5074));
    final Job job = TestJobBuilder.buildCreated(customer, property);

    when(jobRepository.findByIdWithProperty(job.getId())).thenReturn(Optional.of(job));
    when(engineerProfileRepository.findNearbyApproved(anyDouble(), anyDouble()))
        .thenReturn(List.of());
    when(jobRepository.countEngineerJobsTodayBatch(any(), any())).thenReturn(List.of());
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    matchingService.broadcastToEligible(job);

    assertThat(job.getStatus()).isEqualTo(JobStatus.ESCALATED.name());
  }

  @Test
  void broadcastToEligible_setsStatusToAwaitingAcceptance_createsMatchLogs() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    property.setCoordinates(makePoint(-0.1278, 51.5074));
    final Job job = TestJobBuilder.buildCreated(customer, property);

    final User engineerUser = TestUserBuilder.buildActiveEngineer();
    final EngineerProfile ep = buildEngineerProfile(engineerUser, 5);

    when(jobRepository.findByIdWithProperty(job.getId())).thenReturn(Optional.of(job));
    when(engineerProfileRepository.findNearbyApproved(anyDouble(), anyDouble()))
        .thenReturn(List.of(ep));
    when(jobRepository.countEngineerJobsTodayBatch(any(), any())).thenReturn(List.of());
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(matchLogRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

    matchingService.broadcastToEligible(job);

    assertThat(job.getStatus()).isEqualTo(JobStatus.AWAITING_ACCEPTANCE.name());
    assertThat(job.getBroadcastAt()).isNotNull();
    verify(matchLogRepository).saveAll(any());
  }

  @Test
  void broadcastToEligible_jobNotInCreated_skips() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildMatched(customer, property, engineer);

    when(jobRepository.findByIdWithProperty(job.getId())).thenReturn(Optional.of(job));

    matchingService.broadcastToEligible(job);

    verify(matchLogRepository, never()).saveAll(any());
    verify(jobRepository, never()).save(any());
  }

  // ─── claimJob ──────────────────────────────────────────────────────────────────

  @Test
  void claimJob_firstEngineerWins() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);
    job.setStatus(JobStatus.AWAITING_ACCEPTANCE.name());

    final JobMatchLog matchLog = new JobMatchLog();
    matchLog.setId(UUID.randomUUID());
    matchLog.setEngineer(engineer);
    matchLog.setJob(job);
    matchLog.setResponse(MatchLogResponse.PENDING.name());
    matchLog.setCreatedAt(OffsetDateTime.now(clock));
    matchLog.setNotifiedAt(OffsetDateTime.now(clock));

    when(userRepository.findById(engineer.getId())).thenReturn(Optional.of(engineer));
    when(matchLogRepository.findByJobIdAndEngineerId(job.getId(), engineer.getId()))
        .thenReturn(Optional.of(matchLog));
    when(jobRepository.claimJob(eq(job.getId()), eq(engineer), any())).thenReturn(1);
    when(matchLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(jobResponseMapper.toJobResponse(any(), any()))
        .thenReturn(
            new com.uk.certifynow.certify_now.rest.dto.job.JobResponse(
                job.getId(),
                "CN-0001",
                customer.getId(),
                property.getId(),
                "10 Test Street",
                engineer.getId(),
                engineer.getFullName(),
                CertificateType.GAS_SAFETY.name(),
                JobStatus.MATCHED.name(),
                "STANDARD",
                null,
                null,
                1,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null));

    matchingService.claimJob(job.getId(), engineer.getId());

    verify(matchLogRepository).save(any(JobMatchLog.class));
    verify(matchLogRepository).expireAllPendingForJob(job.getId());
    verify(publisher).publishEvent(any(Object.class));
  }

  @Test
  void claimJob_alreadyClaimed_throws409() {
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);

    final JobMatchLog matchLog = new JobMatchLog();
    matchLog.setId(UUID.randomUUID());
    matchLog.setEngineer(engineer);
    matchLog.setJob(job);
    matchLog.setCreatedAt(OffsetDateTime.now(clock));
    matchLog.setNotifiedAt(OffsetDateTime.now(clock));

    when(userRepository.findById(engineer.getId())).thenReturn(Optional.of(engineer));
    when(matchLogRepository.findByJobIdAndEngineerId(job.getId(), engineer.getId()))
        .thenReturn(Optional.of(matchLog));
    when(jobRepository.claimJob(any(), any(), any())).thenReturn(0);

    assertThatThrownBy(() -> matchingService.claimJob(job.getId(), engineer.getId()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getStatus())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void claimJob_engineerNotNotified_throws403() {
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);

    when(userRepository.findById(engineer.getId())).thenReturn(Optional.of(engineer));
    when(matchLogRepository.findByJobIdAndEngineerId(job.getId(), engineer.getId()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> matchingService.claimJob(job.getId(), engineer.getId()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getStatus())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  // ─── parseLocation ────────────────────────────────────────────────────────────

  @Test
  void findCandidates_withCoordinates_queriesNearbyEngineers() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    property.setCoordinates(makePoint(-0.1278, 51.5074));
    final Job job = TestJobBuilder.buildCreated(customer, property);

    when(engineerProfileRepository.findNearbyApproved(51.5074, -0.1278)).thenReturn(List.of());
    when(jobRepository.countEngineerJobsTodayBatch(any(), any())).thenReturn(List.of());

    final List<EngineerProfile> candidates = matchingService.findCandidates(job);
    assertThat(candidates).isEmpty();
  }

  // ─── escalateJob ──────────────────────────────────────────────────────────────

  @Test
  void escalateJob_setsStatusToEscalated_expiresPendingLogs() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);

    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    matchingService.escalateJob(job);

    assertThat(job.getStatus()).isEqualTo(JobStatus.ESCALATED.name());
    assertThat(job.getEscalatedAt()).isNotNull();
    verify(matchLogRepository).expireAllPendingForJob(job.getId());
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────────

  private static Point makePoint(final double lng, final double lat) {
    return new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(lng, lat));
  }

  private EngineerProfile buildEngineerProfile(final User user, final int maxDailyJobs) {
    final EngineerProfile ep = new EngineerProfile();
    ep.setId(UUID.randomUUID());
    ep.setUser(user);
    ep.setStatus(EngineerApplicationStatus.APPROVED);
    ep.setMaxDailyJobs(maxDailyJobs);
    ep.setIsOnline(true);
    ep.setAcceptanceRate(new BigDecimal("0.90"));
    ep.setAvgRating(new BigDecimal("4.80"));
    ep.setOnTimePercentage(new BigDecimal("95.0"));
    ep.setServiceRadiusMiles(new BigDecimal("10.0"));
    ep.setStripeOnboarded(true);
    ep.setTotalJobsCompleted(50);
    ep.setTotalReviews(30);
    ep.setCreatedAt(OffsetDateTime.now(clock).minusDays(30));
    ep.setUpdatedAt(OffsetDateTime.now(clock));
    return ep;
  }
}
