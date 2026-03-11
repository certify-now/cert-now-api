package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.Property;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

  Property findFirstByOwnerId(UUID id);

  org.springframework.data.domain.Page<Property> findByOwnerId(
      UUID ownerId, org.springframework.data.domain.Pageable pageable);

  java.util.List<Property> findByOwnerIdAndIsActiveTrue(UUID ownerId);
}
