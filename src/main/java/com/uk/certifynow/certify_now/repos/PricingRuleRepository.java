package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.PricingRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {

  @Query(
      "SELECT r FROM PricingRule r WHERE r.certificateType = :type "
          + "AND r.isActive = true "
          + "AND r.effectiveFrom <= CURRENT_DATE "
          + "AND (r.effectiveTo IS NULL OR r.effectiveTo > CURRENT_DATE) "
          + "AND r.region = :region "
          + "ORDER BY r.effectiveFrom DESC")
  Optional<PricingRule> findActiveByTypeAndRegion(
      @Param("type") String type, @Param("region") String region);

  @Query(
      "SELECT r FROM PricingRule r WHERE r.certificateType = :type "
          + "AND r.isActive = true "
          + "AND r.region IS NULL "
          + "AND r.effectiveFrom <= CURRENT_DATE "
          + "AND (r.effectiveTo IS NULL OR r.effectiveTo > CURRENT_DATE) "
          + "ORDER BY r.effectiveFrom DESC")
  Optional<PricingRule> findNationalDefault(@Param("type") String type);

  List<PricingRule> findByIsActiveTrue();
}
