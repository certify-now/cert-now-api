package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.PricingModifier;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingModifierRepository extends JpaRepository<PricingModifier, UUID> {

  PricingModifier findFirstByPricingRuleId(UUID id);

  List<PricingModifier> findByPricingRuleId(UUID pricingRuleId);
}
