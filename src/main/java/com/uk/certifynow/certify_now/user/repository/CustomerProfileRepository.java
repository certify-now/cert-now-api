package com.uk.certifynow.certify_now.user.repository;

import com.uk.certifynow.certify_now.domain.CustomerProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {

  Optional<CustomerProfile> findFirstByUserId(UUID userId);
}
