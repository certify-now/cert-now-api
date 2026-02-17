package com.uk.certifynow.certify_now.user.repository;

import com.uk.certifynow.certify_now.domain.RefreshToken;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  List<RefreshToken> findAllByUserIdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtAsc(
      UUID userId, OffsetDateTime now);
}
