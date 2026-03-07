package com.uk.certifynow.certify_now.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.service.matching.MatchingService;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchingSchedulerTest {

  @Mock private JobRepository jobRepository;
  @Mock private MatchingService matchingService;

  @InjectMocks private MatchingScheduler matchingScheduler;

  // ════════════════════════════════════════════════════════════════════════════
  // processUnmatchedJobs()
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("processUnmatchedJobs()")
  class ProcessUnmatchedTests {

    @Test
    @DisplayName("Does nothing when no unmatched jobs exist")
    void noUnmatchedJobs() {
      when(jobRepository.findByStatusAndBroadcastAtIsNull("CREATED"))
          .thenReturn(Collections.emptyList());

      matchingScheduler.processUnmatchedJobs();

      verify(matchingService, never()).broadcastToEligible(any());
    }

    @Test
    @DisplayName("Broadcasts each unmatched job")
    void broadcastsUnmatchedJobs() {
      final Job job1 = new Job();
      job1.setId(UUID.randomUUID());
      final Job job2 = new Job();
      job2.setId(UUID.randomUUID());

      when(jobRepository.findByStatusAndBroadcastAtIsNull("CREATED"))
          .thenReturn(List.of(job1, job2));

      matchingScheduler.processUnmatchedJobs();

      verify(matchingService).broadcastToEligible(job1);
      verify(matchingService).broadcastToEligible(job2);
    }

    @Test
    @DisplayName("Continues processing remaining jobs when one fails")
    void continuesOnFailure() {
      final Job job1 = new Job();
      job1.setId(UUID.randomUUID());
      final Job job2 = new Job();
      job2.setId(UUID.randomUUID());

      when(jobRepository.findByStatusAndBroadcastAtIsNull("CREATED"))
          .thenReturn(List.of(job1, job2));
      doThrow(new RuntimeException("DB error")).when(matchingService).broadcastToEligible(job1);

      matchingScheduler.processUnmatchedJobs();

      verify(matchingService).broadcastToEligible(job1);
      verify(matchingService).broadcastToEligible(job2);
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // processExpiredBroadcasts()
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("processExpiredBroadcasts()")
  class ProcessExpiredTests {

    @Test
    @DisplayName("Does nothing when no expired broadcasts exist")
    void noExpiredBroadcasts() {
      when(jobRepository.findByStatusAndBroadcastAtBefore(
              eq("AWAITING_ACCEPTANCE"), any(OffsetDateTime.class)))
          .thenReturn(Collections.emptyList());

      matchingScheduler.processExpiredBroadcasts();

      verify(matchingService, never()).escalateJob(any());
    }

    @Test
    @DisplayName("Escalates each expired broadcast")
    void escalatesExpiredBroadcasts() {
      final Job job1 = new Job();
      job1.setId(UUID.randomUUID());

      when(jobRepository.findByStatusAndBroadcastAtBefore(
              eq("AWAITING_ACCEPTANCE"), any(OffsetDateTime.class)))
          .thenReturn(List.of(job1));

      matchingScheduler.processExpiredBroadcasts();

      verify(matchingService).escalateJob(job1);
    }

    @Test
    @DisplayName("Continues processing when one escalation fails")
    void continuesOnFailure() {
      final Job job1 = new Job();
      job1.setId(UUID.randomUUID());
      final Job job2 = new Job();
      job2.setId(UUID.randomUUID());

      when(jobRepository.findByStatusAndBroadcastAtBefore(
              eq("AWAITING_ACCEPTANCE"), any(OffsetDateTime.class)))
          .thenReturn(List.of(job1, job2));
      doThrow(new RuntimeException("DB error")).when(matchingService).escalateJob(job1);

      matchingScheduler.processExpiredBroadcasts();

      verify(matchingService).escalateJob(job1);
      verify(matchingService).escalateJob(job2);
    }
  }
}
