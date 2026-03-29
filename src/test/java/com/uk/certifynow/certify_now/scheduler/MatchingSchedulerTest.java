package com.uk.certifynow.certify_now.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.service.job.JobStatus;
import com.uk.certifynow.certify_now.service.matching.MatchingService;
import com.uk.certifynow.certify_now.util.TestConstants;
import com.uk.certifynow.certify_now.util.TestJobBuilder;
import com.uk.certifynow.certify_now.util.TestPropertyBuilder;
import com.uk.certifynow.certify_now.util.TestUserBuilder;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class MatchingSchedulerTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  @Mock private JobRepository jobRepository;
  @Mock private MatchingService matchingService;

  private MatchingScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new MatchingScheduler(jobRepository, matchingService, clock);
  }

  @Test
  void processUnmatchedJobs_noJobs_doesNothing() {
    when(jobRepository.findByStatusAndBroadcastAtIsNull(anyString(), any(Pageable.class)))
        .thenReturn(Page.empty());

    scheduler.processUnmatchedJobs();

    verify(matchingService, never()).broadcastToEligible(any());
  }

  @Test
  void processUnmatchedJobs_broadcastsEachJob() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job1 = TestJobBuilder.buildCreated(customer, property);
    final Job job2 = TestJobBuilder.buildCreated(customer, property);

    final Page<Job> page = new PageImpl<>(List.of(job1, job2));
    when(jobRepository.findByStatusAndBroadcastAtIsNull(
            eq(JobStatus.CREATED.name()), any(Pageable.class)))
        .thenReturn(page)
        .thenReturn(Page.empty());

    scheduler.processUnmatchedJobs();

    verify(matchingService, times(2)).broadcastToEligible(any(Job.class));
  }

  @Test
  void processUnmatchedJobs_failedBroadcast_continuesWithNext() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job1 = TestJobBuilder.buildCreated(customer, property);
    final Job job2 = TestJobBuilder.buildCreated(customer, property);

    final Page<Job> page = new PageImpl<>(List.of(job1, job2));
    when(jobRepository.findByStatusAndBroadcastAtIsNull(anyString(), any(Pageable.class)))
        .thenReturn(page)
        .thenReturn(Page.empty());

    // First job throws, second should still be processed
    org.mockito.Mockito.doThrow(new RuntimeException("broadcast failed"))
        .when(matchingService)
        .broadcastToEligible(job1);

    scheduler.processUnmatchedJobs();

    verify(matchingService).broadcastToEligible(job1);
    verify(matchingService).broadcastToEligible(job2);
  }

  @Test
  void processExpiredBroadcasts_escalatesExpiredJobs() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);
    job.setStatus(JobStatus.AWAITING_ACCEPTANCE.name());

    final Page<Job> page = new PageImpl<>(List.of(job));
    when(jobRepository.findByStatusAndBroadcastAtBefore(
            eq(JobStatus.AWAITING_ACCEPTANCE.name()), any(), any(Pageable.class)))
        .thenReturn(page)
        .thenReturn(Page.empty());

    scheduler.processExpiredBroadcasts();

    verify(matchingService).escalateJob(job);
  }

  @Test
  void processExpiredBroadcasts_noExpiredJobs_doesNothing() {
    when(jobRepository.findByStatusAndBroadcastAtBefore(anyString(), any(), any(Pageable.class)))
        .thenReturn(Page.empty());

    scheduler.processExpiredBroadcasts();

    verify(matchingService, never()).escalateJob(any());
  }

  @Test
  void processUnmatchedJobs_alwaysFetchesPageZero() {
    // Jobs transition out of CREATED status mid-iteration, so we always fetch page 0
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);

    final Page<Job> firstPage = new PageImpl<>(List.of(job), PageRequest.of(0, 50), 1);
    when(jobRepository.findByStatusAndBroadcastAtIsNull(anyString(), any(Pageable.class)))
        .thenReturn(firstPage)
        .thenReturn(Page.empty());

    scheduler.processUnmatchedJobs();

    // After broadcasting the job, it moves to AWAITING_ACCEPTANCE — next iteration returns empty
    verify(matchingService).broadcastToEligible(job);
  }
}
