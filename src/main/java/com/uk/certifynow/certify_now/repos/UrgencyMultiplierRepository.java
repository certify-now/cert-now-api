package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.UrgencyMultiplier;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UrgencyMultiplierRepository extends JpaRepository<UrgencyMultiplier, UUID> {}
