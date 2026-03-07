package com.uk.certifynow.certify_now.events.job;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.service.matching.MatchingService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchingJobListenerTest {

  @Mock private MatchingService matchingService;
  @Mock private JobRepository jobRepository;

  @InjectMocks private MatchingJobListener listener;

  @Test
  @DisplayName("Calls broadcastToEligible when job is found")
  void callsBroadcast() {
    final UUID jobId = UUID.randomUUID();
    final Job job = new Job();
    job.setId(jobId);

    when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

    listener.onJobCreated(new JobCreatedEvent(jobId, null, null, null, 0));

    verify(matchingService).broadcastToEligible(job);
  }

  @Test
  @DisplayName("Throws if job not found after creation event")
  void throwsWhenJobNotFound() {
    final UUID jobId = UUID.randomUUID();

    when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

    Assertions.assertThrows(
        IllegalStateException.class,
        () -> listener.onJobCreated(new JobCreatedEvent(jobId, null, null, null, 0)));
  }

  @Test
  @DisplayName("Logs error and does not rethrow when broadcastToEligible fails")
  void handlesExceptionGracefully() {
    final UUID jobId = UUID.randomUUID();
    final Job job = new Job();
    job.setId(jobId);

    when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
    doThrow(new RuntimeException("DB error")).when(matchingService).broadcastToEligible(job);

    // Should not throw — error is caught and logged
    listener.onJobCreated(new JobCreatedEvent(jobId, null, null, null, 0));

    verify(matchingService).broadcastToEligible(job);
  }
}
