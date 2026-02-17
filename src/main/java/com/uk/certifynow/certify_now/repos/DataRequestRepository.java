package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.DataRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataRequestRepository extends JpaRepository<DataRequest, UUID> {

  DataRequest findFirstByUserId(UUID id);
}
