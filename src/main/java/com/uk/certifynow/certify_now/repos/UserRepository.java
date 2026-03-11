package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  boolean existsByPhone(String phone);

  Optional<User> findByPhone(String phone);

  // ── Soft-delete admin queries (bypass @SQLRestriction via native SQL) ────

  @Query(value = "SELECT * FROM \"user\" WHERE id = :id", nativeQuery = true)
  Optional<User> findByIdIncludingDeleted(@Param("id") UUID id);

  @Query(value = "SELECT * FROM \"user\" WHERE deleted_at IS NOT NULL", nativeQuery = true)
  List<User> findAllDeleted();

  @Query(value = "SELECT * FROM \"user\"", nativeQuery = true)
  List<User> findAllIncludingDeleted();

  @Query(
      value = "SELECT * FROM \"user\" WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff",
      nativeQuery = true)
  List<User> findAllDeletedBefore(@Param("cutoff") OffsetDateTime cutoff);
}
