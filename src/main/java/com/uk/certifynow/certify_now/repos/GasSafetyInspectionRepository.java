package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.GasSafetyInspection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GasSafetyInspectionRepository extends JpaRepository<GasSafetyInspection, UUID> {

  GasSafetyInspection findFirstByJobId(UUID id);
}
