package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.EngineerQualification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineerQualificationRepository
    extends JpaRepository<EngineerQualification, UUID> {

  EngineerQualification findFirstByEngineerProfileId(UUID id);

  List<EngineerQualification> findAllByEngineerProfileId(UUID profileId);

  boolean existsByEngineerProfileIdAndType(UUID profileId, String type);
}
