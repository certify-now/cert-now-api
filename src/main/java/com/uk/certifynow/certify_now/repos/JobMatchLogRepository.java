package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.JobMatchLog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobMatchLogRepository extends JpaRepository<JobMatchLog, UUID> {

  /** All match attempts for a job, most recent first. */
  List<JobMatchLog> findByJobIdOrderByCreatedAtDesc(UUID jobId);

  /** Find the specific match log entry for a job+engineer pair. */
  Optional<JobMatchLog> findByJobIdAndEngineerId(UUID jobId, UUID engineerId);

  /** Used by CRUD stub (JobMatchLogService BeforeDelete listener). */
  JobMatchLog findFirstByEngineerId(UUID engineerId);

  /** Used by CRUD stub (JobMatchLogService BeforeDelete listener). */
  JobMatchLog findFirstByJobId(UUID jobId);
}
