package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.CustomerProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {

  CustomerProfile findFirstByUserId(UUID id);

  // ── Soft-delete admin queries (bypass @SQLRestriction via native SQL) ────

  @Query(value = "SELECT * FROM customer_profile WHERE id = :id", nativeQuery = true)
  Optional<CustomerProfile> findByIdIncludingDeleted(@Param("id") UUID id);

  @Query(value = "SELECT * FROM customer_profile WHERE deleted_at IS NOT NULL", nativeQuery = true)
  List<CustomerProfile> findAllDeleted();

  @Query(value = "SELECT * FROM customer_profile", nativeQuery = true)
  List<CustomerProfile> findAllIncludingDeleted();

  @Query(value = "SELECT * FROM customer_profile WHERE user_id = :userId", nativeQuery = true)
  Optional<CustomerProfile> findByUserIdIncludingDeleted(@Param("userId") UUID userId);
}
