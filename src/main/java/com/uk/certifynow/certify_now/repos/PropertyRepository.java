package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.Property;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

  Property findFirstByOwnerId(UUID id);
}
