package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.Certificate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;


public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

    Certificate findFirstByIssuedByEngineerId(UUID id);

    Certificate findFirstByJobId(UUID id);

    Certificate findFirstByPropertyId(UUID id);

    Certificate findFirstBySupersededByIdAndIdNot(UUID id, UUID currentId);

}
