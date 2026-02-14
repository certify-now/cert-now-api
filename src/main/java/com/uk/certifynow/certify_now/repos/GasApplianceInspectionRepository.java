package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.GasApplianceInspection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;


public interface GasApplianceInspectionRepository extends JpaRepository<GasApplianceInspection, UUID> {

    GasApplianceInspection findFirstByGasInspectionId(UUID id);

}
