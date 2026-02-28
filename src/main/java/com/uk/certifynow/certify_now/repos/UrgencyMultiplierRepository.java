package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.UrgencyMultiplier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UrgencyMultiplierRepository extends JpaRepository<UrgencyMultiplier, UUID> {

  @Query(
      "SELECT m FROM UrgencyMultiplier m WHERE m.urgency = :urgency "
          + "AND m.isActive = true "
          + "AND m.effectiveFrom <= CURRENT_DATE "
          + "ORDER BY m.effectiveFrom DESC")
  Optional<UrgencyMultiplier> findActiveByUrgency(@Param("urgency") String urgency);

  List<UrgencyMultiplier> findByIsActiveTrue();
}
