package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.EngineerAvailability;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface EngineerAvailabilityRepository extends JpaRepository<EngineerAvailability, UUID> {

  EngineerAvailability findFirstByEngineerProfileId(UUID id);

  List<EngineerAvailability> findAllByEngineerProfileId(UUID profileId);

  List<EngineerAvailability> findAllByEngineerProfileIdAndIsRecurring(
      UUID profileId, Boolean isRecurring);

  List<EngineerAvailability> findAllByEngineerProfileIdAndOverrideDate(
      UUID profileId, LocalDate date);

  @Modifying
  @Transactional
  void deleteAllByEngineerProfileIdAndIsRecurring(UUID profileId, Boolean isRecurring);
}
