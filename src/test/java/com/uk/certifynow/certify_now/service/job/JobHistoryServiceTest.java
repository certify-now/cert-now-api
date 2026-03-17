package com.uk.certifynow.certify_now.service.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.JobMatchLog;
import com.uk.certifynow.certify_now.domain.JobStatusHistory;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.JobMatchLogRepository;
import com.uk.certifynow.certify_now.repos.JobStatusHistoryRepository;
import com.uk.certifynow.certify_now.rest.dto.job.JobStatusHistoryResponse;
import com.uk.certifynow.certify_now.util.TestConstants;
import com.uk.certifynow.certify_now.util.TestJobBuilder;
import com.uk.certifynow.certify_now.util.TestPropertyBuilder;
import com.uk.certifynow.certify_now.util.TestUserBuilder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobHistoryServiceTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  @Mock private JobStatusHistoryRepository historyRepository;
  @Mock private JobMatchLogRepository matchLogRepository;

  private JobHistoryService service;

  @BeforeEach
  void setUp() {
    service = new JobHistoryService(historyRepository, matchLogRepository, clock);
  }

  @Test
  void recordHistory_savesAllFields() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);
    final UUID actorId = UUID.randomUUID();

    when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.recordHistory(
        job, null, "CREATED", actorId, "CUSTOMER", "initial", "{\"key\":\"val\"}");

    final ArgumentCaptor<JobStatusHistory> captor = ArgumentCaptor.forClass(JobStatusHistory.class);
    verify(historyRepository).save(captor.capture());

    final JobStatusHistory saved = captor.getValue();
    assertThat(saved.getFromStatus()).isNull();
    assertThat(saved.getToStatus()).isEqualTo("CREATED");
    assertThat(saved.getActorId()).isEqualTo(actorId);
    assertThat(saved.getActorType()).isEqualTo("CUSTOMER");
    assertThat(saved.getReason()).isEqualTo("initial");
    assertThat(saved.getMetadata()).isEqualTo("{\"key\":\"val\"}");
    assertThat(saved.getCreatedAt()).isEqualTo(TestConstants.FIXED_NOW);
  }

  @Test
  void createMatchLog_savesWithTimestamps() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);

    when(matchLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.createMatchLog(job, engineer, new BigDecimal("0.95"), new BigDecimal("2.5"));

    final ArgumentCaptor<JobMatchLog> captor = ArgumentCaptor.forClass(JobMatchLog.class);
    verify(matchLogRepository).save(captor.capture());

    final JobMatchLog saved = captor.getValue();
    assertThat(saved.getJob()).isEqualTo(job);
    assertThat(saved.getEngineer()).isEqualTo(engineer);
    assertThat(saved.getMatchScore()).isEqualByComparingTo("0.95");
    assertThat(saved.getDistanceMiles()).isEqualByComparingTo("2.5");
    assertThat(saved.getNotifiedAt()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  void getHistoryResponses_returnsOrderedByCreatedAt() {
    final UUID jobId = UUID.randomUUID();
    final OffsetDateTime t1 = OffsetDateTime.now(clock).minusHours(2);
    final OffsetDateTime t2 = OffsetDateTime.now(clock).minusHours(1);

    final JobStatusHistory h1 = buildHistory(jobId, null, "CREATED", t1);
    final JobStatusHistory h2 = buildHistory(jobId, "CREATED", "MATCHED", t2);

    when(historyRepository.findByJobIdOrderByCreatedAtAsc(jobId)).thenReturn(List.of(h1, h2));

    final List<JobStatusHistoryResponse> result = service.getHistoryResponses(jobId);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).toStatus()).isEqualTo("CREATED");
    assertThat(result.get(1).toStatus()).isEqualTo("MATCHED");
  }

  private JobStatusHistory buildHistory(
      final UUID jobId, final String from, final String to, final OffsetDateTime createdAt) {
    final JobStatusHistory h = new JobStatusHistory();
    h.setId(UUID.randomUUID());
    h.setFromStatus(from);
    h.setToStatus(to);
    h.setActorType("CUSTOMER");
    h.setCreatedAt(createdAt);
    final Job job = new Job();
    job.setId(jobId);
    h.setJob(job);
    return h;
  }
}
