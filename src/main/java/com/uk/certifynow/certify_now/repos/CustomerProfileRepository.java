package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.CustomerProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {

  CustomerProfile findFirstByUserId(UUID id);
}
