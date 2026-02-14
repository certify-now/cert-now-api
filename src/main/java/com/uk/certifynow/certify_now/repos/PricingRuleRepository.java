package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.PricingRule;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;


public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {
}
