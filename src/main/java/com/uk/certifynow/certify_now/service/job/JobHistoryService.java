package com.uk.certifynow.certify_now.service.job;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.JobMatchLog;
import com.uk.certifynow.certify_now.domain.JobStatusHistory;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.JobMatchLogRepository;
import com.uk.certifynow.certify_now.repos.JobStatusHistoryRepository;
import com.uk.certifynow.certify_now.rest.dto.job.JobStatusHistoryResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages job status history recording and match log creation. */
@Service
public class JobHistoryService {

  private final JobStatusHistoryRepository historyRepository;
  private final JobMatchLogRepository matchLogRepository;
  private final Clock clock;

  public JobHistoryService(
      final JobStatusHistoryRepository historyRepository,
      final JobMatchLogRepository matchLogRepository,
      final Clock clock) {
    this.historyRepository = historyRepository;
    this.matchLogRepository = matchLogRepository;
    this.clock = clock;
  }

  @Transactional
  public void recordHistory(
      final Job job,
      final String fromStatus,
      final String toStatus,
      final UUID actorId,
      final String actorType,
      final String reason,
      final String metadata) {
    final JobStatusHistory history = new JobStatusHistory();
    history.setJob(job);
    history.setFromStatus(fromStatus);
    history.setToStatus(toStatus);
    history.setActorId(actorId);
    history.setActorType(actorType);
    history.setReason(reason);
    history.setMetadata(metadata);
    history.setCreatedAt(OffsetDateTime.now(clock));
    historyRepository.save(history);
  }

  @Transactional
  public void createMatchLog(
      final Job job, final User engineer, final BigDecimal score, final BigDecimal distance) {
    final JobMatchLog log = new JobMatchLog();
    log.setJob(job);
    log.setEngineer(engineer);
    log.setMatchScore(score);
    log.setDistanceMiles(distance);
    log.setNotifiedAt(OffsetDateTime.now(clock));
    log.setOfferedAt(OffsetDateTime.now(clock));
    log.setCreatedAt(OffsetDateTime.now(clock));
    matchLogRepository.save(log);
  }

  @Transactional(readOnly = true)
  public List<JobStatusHistoryResponse> getHistoryResponses(final UUID jobId) {
    return historyRepository.findByJobIdOrderByCreatedAtAsc(jobId).stream()
        .map(
            h ->
                new JobStatusHistoryResponse(
                    h.getId(),
                    h.getFromStatus(),
                    h.getToStatus(),
                    h.getActorId(),
                    h.getActorType(),
                    h.getReason(),
                    h.getMetadata(),
                    h.getCreatedAt()))
        .toList();
  }
}
