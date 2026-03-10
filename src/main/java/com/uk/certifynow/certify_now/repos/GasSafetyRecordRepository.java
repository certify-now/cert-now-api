package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.GasSafetyRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GasSafetyRecordRepository extends JpaRepository<GasSafetyRecord, UUID> {

  Optional<GasSafetyRecord> findByJobId(UUID jobId);

  @Query("SELECT r FROM GasSafetyRecord r LEFT JOIN FETCH r.appliances WHERE r.job.id = :jobId")
  Optional<GasSafetyRecord> findByJobIdWithAppliances(@Param("jobId") UUID jobId);

  boolean existsByJobId(UUID jobId);
}
