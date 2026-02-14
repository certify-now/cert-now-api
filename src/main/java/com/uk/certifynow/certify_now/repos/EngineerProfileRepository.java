package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.EngineerProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;


public interface EngineerProfileRepository extends JpaRepository<EngineerProfile, UUID> {

    EngineerProfile findFirstByUserId(UUID id);

}
