package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.JobStatusHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;


public interface JobStatusHistoryRepository extends JpaRepository<JobStatusHistory, UUID> {

    JobStatusHistory findFirstByJobId(UUID id);

}
