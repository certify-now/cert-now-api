package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.FeatureFlag;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {}
