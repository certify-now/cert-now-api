package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.EngineerInsurance;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineerInsuranceRepository extends JpaRepository<EngineerInsurance, UUID> {

  EngineerInsurance findFirstByEngineerProfileId(UUID id);

  List<EngineerInsurance> findAllByEngineerProfileId(UUID profileId);

  Optional<EngineerInsurance> findByEngineerProfileIdAndPolicyType(
      UUID profileId, String policyType);
}
