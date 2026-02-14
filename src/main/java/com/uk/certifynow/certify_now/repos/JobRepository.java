package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.Job;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;


public interface JobRepository extends JpaRepository<Job, UUID> {

    Job findFirstByCustomerId(UUID id);

    Job findFirstByEngineerId(UUID id);

    Job findFirstByPropertyId(UUID id);

}
