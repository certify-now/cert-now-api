package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.UserConsent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;


public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {

    UserConsent findFirstByUserId(UUID id);

}
