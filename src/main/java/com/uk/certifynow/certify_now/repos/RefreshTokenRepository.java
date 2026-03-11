package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.RefreshToken;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  RefreshToken findFirstByUserId(UUID id);

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  List<RefreshToken> findAllByUserIdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtAsc(
      UUID userId, OffsetDateTime now);

  /** Fix 5: Find all tokens belonging to a token family (for theft detection revocation). */
  List<RefreshToken> findAllByFamilyId(UUID familyId);

  /**
   * Delete all refresh tokens for a given user (used during soft-delete to invalidate sessions).
   */
  void deleteAllByUserId(UUID userId);
}
