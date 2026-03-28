package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.ShareToken;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShareTokenRepository extends JpaRepository<ShareToken, UUID> {

  @Query("SELECT t FROM ShareToken t JOIN FETCH t.certificate WHERE t.token = :token")
  Optional<ShareToken> findByToken(@Param("token") String token);

  List<ShareToken> findAllByCertificateId(UUID certificateId);

  @Query(
      "SELECT t FROM ShareToken t WHERE t.certificate.id = :certId AND t.expiresAt > :now ORDER BY t.createdAt DESC")
  List<ShareToken> findActiveByCertificateId(
      @Param("certId") UUID certId, @Param("now") OffsetDateTime now);

  @Modifying
  @Query("DELETE FROM ShareToken t WHERE t.expiresAt < :now")
  int deleteExpiredTokens(@Param("now") OffsetDateTime now);

  void deleteByCertificateId(UUID certificateId);
}
