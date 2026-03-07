package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.GasSafetyRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GasSafetyRecordRepository extends JpaRepository<GasSafetyRecord, UUID> {

  Optional<GasSafetyRecord> findByJobId(UUID jobId);

  boolean existsByJobId(UUID jobId);
}
