package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.service.auth.EngineerApplicationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineerProfileRepository extends JpaRepository<EngineerProfile, UUID> {

  EngineerProfile findFirstByUserId(UUID id);

  Optional<EngineerProfile> findByUserId(UUID userId);

  List<EngineerProfile> findByStatusAndIsOnline(EngineerApplicationStatus status, Boolean isOnline);

  List<EngineerProfile> findByStatus(EngineerApplicationStatus status);
}
