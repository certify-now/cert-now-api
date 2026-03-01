package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.JobStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobStatusHistoryRepository extends JpaRepository<JobStatusHistory, UUID> {

  /** Returns the full state transition trail for a job, oldest first. */
  List<JobStatusHistory> findByJobIdOrderByCreatedAtAsc(UUID jobId);

  /** Used by CRUD stub (JobStatusHistoryService BeforeDelete listener). */
  JobStatusHistory findFirstByJobId(UUID jobId);
}
