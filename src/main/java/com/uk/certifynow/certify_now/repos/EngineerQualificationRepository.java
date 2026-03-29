package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.EngineerQualification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EngineerQualificationRepository
    extends JpaRepository<EngineerQualification, UUID> {

  EngineerQualification findFirstByEngineerProfileId(UUID id);

  List<EngineerQualification> findAllByEngineerProfileId(UUID profileId);

  long countByEngineerProfileId(UUID profileId);

  boolean existsByEngineerProfileIdAndType(UUID profileId, String type);

  @Query(
      "SELECT eq.engineerProfile.id, COUNT(eq) FROM EngineerQualification eq"
          + " WHERE eq.engineerProfile.id IN :profileIds"
          + " GROUP BY eq.engineerProfile.id")
  List<Object[]> countByEngineerProfileIds(@Param("profileIds") List<UUID> profileIds);
}
