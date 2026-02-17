package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.EngineerAvailability;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineerAvailabilityRepository extends JpaRepository<EngineerAvailability, UUID> {

  EngineerAvailability findFirstByEngineerProfileId(UUID id);
}
