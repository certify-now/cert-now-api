package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.Certificate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

  Certificate findFirstByIssuedByEngineerId(UUID id);

  Certificate findFirstByJobId(UUID id);

  Certificate findFirstByPropertyId(UUID id);

  Certificate findFirstBySupersededByIdAndIdNot(UUID id, UUID currentId);

  List<Certificate> findByPropertyIdAndCertificateTypeAndStatus(
      UUID propertyId, String certificateType, String status);

  // ── Customer-facing queries ───────────────────────────────────────────────

  /**
   * Returns all non-superseded certificates for properties owned by the given customer, with
   * optional filters on certificate type, property, and entity status.
   *
   * <p>The {@code Property} entity carries {@code @SQLRestriction("deleted_at IS NULL")} so
   * soft-deleted properties are automatically excluded via the JOIN.
   */
  @Query(
      """
      SELECT c FROM Certificate c
      JOIN FETCH c.property p
      LEFT JOIN FETCH c.issuedByEngineer e
      WHERE p.owner.id = :ownerId
        AND c.status <> 'SUPERSEDED'
        AND (:type IS NULL OR c.certificateType = :type)
        AND (:propertyId IS NULL OR p.id = :propertyId)
      ORDER BY c.issuedAt DESC
      """)
  List<Certificate> findByPropertyOwnerIdWithFilters(
      @Param("ownerId") UUID ownerId,
      @Param("type") String type,
      @Param("propertyId") UUID propertyId);

  /**
   * Returns ALL certificates (including superseded) for properties owned by the given customer.
   * Used when the customer explicitly requests their full certificate history. Also eagerly fetches
   * supersededBy so callers can resolve the replacement chain without N+1.
   */
  @Query(
      """
      SELECT c FROM Certificate c
      JOIN FETCH c.property p
      LEFT JOIN FETCH c.issuedByEngineer e
      LEFT JOIN FETCH c.supersededBy sc
      WHERE p.owner.id = :ownerId
        AND (:type IS NULL OR c.certificateType = :type)
        AND (:propertyId IS NULL OR p.id = :propertyId)
      ORDER BY c.issuedAt DESC
      """)
  List<Certificate> findByPropertyOwnerIdWithHistory(
      @Param("ownerId") UUID ownerId,
      @Param("type") String type,
      @Param("propertyId") UUID propertyId);

  /** Fetches a single certificate with all detail associations loaded in one query to avoid N+1. */
  @Query(
      """
      SELECT c FROM Certificate c
      JOIN FETCH c.property p
      JOIN FETCH p.owner
      LEFT JOIN FETCH c.issuedByEngineer
      LEFT JOIN FETCH c.job j
      LEFT JOIN FETCH c.documents cd
      LEFT JOIN FETCH cd.document
      WHERE c.id = :id
      """)
  Optional<Certificate> findByIdWithDetails(@Param("id") UUID id);

  /**
   * Checks whether an active (non-superseded, non-expired) certificate of the given type exists for
   * a property. Used for missing-certificate detection.
   */
  @Query(
      """
      SELECT c FROM Certificate c
      WHERE c.property.id = :propertyId
        AND c.certificateType = :certificateType
        AND c.status = 'ACTIVE'
        AND (c.expiryAt IS NULL OR c.expiryAt >= :today)
      ORDER BY c.issuedAt DESC
      """)
  List<Certificate> findActiveCertificateByPropertyAndType(
      @Param("propertyId") UUID propertyId,
      @Param("certificateType") String certificateType,
      @Param("today") LocalDate today);

  /** Used for the download/share ownership check: fastest path by ID only. */
  @Query("SELECT c FROM Certificate c JOIN FETCH c.property p JOIN FETCH p.owner WHERE c.id = :id")
  Optional<Certificate> findByIdWithProperty(@Param("id") UUID id);

  /**
   * Batch-loads all non-superseded certificates for multiple properties in a single query. Used by
   * CustomerCertificateService to avoid N×type per-property lookups. The caller partitions by
   * (propertyId, certificateType) and filters by expiry date in memory.
   */
  @Query(
      """
      SELECT c FROM Certificate c
      WHERE c.property.id IN :propertyIds
        AND c.status <> 'SUPERSEDED'
      """)
  List<Certificate> findAllActiveCertsByPropertyIds(@Param("propertyIds") List<UUID> propertyIds);

  /** Find by share token for the public share endpoint. */
  Optional<Certificate> findByShareToken(String shareToken);
}
