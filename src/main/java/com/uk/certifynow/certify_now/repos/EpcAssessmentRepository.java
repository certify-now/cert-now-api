package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.EpcAssessment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EpcAssessmentRepository extends JpaRepository<EpcAssessment, UUID> {

  Optional<EpcAssessment> findByJobId(UUID jobId);

  boolean existsByJobId(UUID jobId);
}
