package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.EngineerInsurance;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;


public interface EngineerInsuranceRepository extends JpaRepository<EngineerInsurance, UUID> {

    EngineerInsurance findFirstByEngineerProfileId(UUID id);

}
