package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.JobMatchLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;


public interface JobMatchLogRepository extends JpaRepository<JobMatchLog, UUID> {

    JobMatchLog findFirstByEngineerId(UUID id);

    JobMatchLog findFirstByJobId(UUID id);

}
