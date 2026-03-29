package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.EngineerInsurance;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EngineerInsuranceRepository extends JpaRepository<EngineerInsurance, UUID> {

  EngineerInsurance findFirstByEngineerProfileId(UUID id);

  List<EngineerInsurance> findAllByEngineerProfileId(UUID profileId);

  long countByEngineerProfileId(UUID profileId);

  Optional<EngineerInsurance> findByEngineerProfileIdAndPolicyType(
      UUID profileId, String policyType);

  @Query(
      "SELECT ei.engineerProfile.id, COUNT(ei) FROM EngineerInsurance ei"
          + " WHERE ei.engineerProfile.id IN :profileIds"
          + " GROUP BY ei.engineerProfile.id")
  List<Object[]> countByEngineerProfileIds(@Param("profileIds") List<UUID> profileIds);
}
