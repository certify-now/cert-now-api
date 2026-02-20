package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.EmailVerificationToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for email verification tokens.
 */
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    /**
     * Find token by hash.
     *
     * @param tokenHash SHA-256 hash of the raw token
     * @return optional token entity
     */
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * Delete all expired and used tokens for cleanup.
     *
     * @param now current timestamp
     * @return number of deleted tokens
     */
    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :now OR t.usedAt IS NOT NULL")
    int deleteExpiredAndUsedTokens(@Param("now") java.time.OffsetDateTime now);

    /**
     * Delete all tokens for a specific user.
     * Used when user requests a new verification email.
     *
     * @param userId user ID
     */
    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.user.id = :userId AND t.usedAt IS NULL")
    void deleteUnusedTokensByUserId(@Param("userId") UUID userId);
}
