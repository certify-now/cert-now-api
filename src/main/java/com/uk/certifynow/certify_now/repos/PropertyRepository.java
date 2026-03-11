package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.Property;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

  Property findFirstByOwnerId(UUID id);

  Page<Property> findByOwnerId(UUID ownerId, Pageable pageable);

  List<Property> findByOwnerIdAndIsActiveTrue(UUID ownerId, Sort sort);
}
