package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.EicrInspection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EicrInspectionRepository extends JpaRepository<EicrInspection, UUID> {

  EicrInspection findFirstByJobId(UUID id);
}
