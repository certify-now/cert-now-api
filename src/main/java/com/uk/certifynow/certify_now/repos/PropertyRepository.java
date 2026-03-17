package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.Property;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

  Property findFirstByOwnerId(UUID id);

  Page<Property> findByOwnerId(UUID ownerId, Pageable pageable);

  Page<Property> findByOwnerIdAndIsActiveTrue(UUID ownerId, Pageable pageable);

  List<Property> findByOwnerIdAndIsActiveTrue(UUID ownerId, Sort sort);

  boolean existsByOwnerIdAndAddressLine1IgnoreCaseAndPostcodeIgnoreCase(
      UUID ownerId, String addressLine1, String postcode);

  boolean existsByOwnerIdAndAddressLine1IgnoreCaseAndPostcodeIgnoreCaseAndIdNot(
      UUID ownerId, String addressLine1, String postcode, UUID excludeId);

  // ── Soft-delete admin queries (bypass @SQLRestriction via native SQL) ────

  @Query(value = "SELECT * FROM property WHERE id = :id", nativeQuery = true)
  Optional<Property> findByIdIncludingDeleted(@Param("id") UUID id);

  @Query(value = "SELECT * FROM property WHERE deleted_at IS NOT NULL", nativeQuery = true)
  List<Property> findAllDeleted();

  @Query(value = "SELECT * FROM property", nativeQuery = true)
  List<Property> findAllIncludingDeleted();
}
