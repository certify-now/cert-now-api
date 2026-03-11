package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, UUID> {

  // ── Used by BeforeDelete event listeners (existing) ───────────────────────
  Job findFirstByCustomerId(UUID id);

  Job findFirstByEngineerId(UUID id);

  Job findFirstByPropertyId(UUID id);

  // ── Customer list queries ──────────────────────────────────────────────────
  Page<Job> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

  Page<Job> findByCustomerIdAndStatusOrderByCreatedAtDesc(
      UUID customerId, String status, Pageable pageable);

  Page<Job> findByCustomerIdAndCertificateTypeOrderByCreatedAtDesc(
      UUID customerId, String certificateType, Pageable pageable);

  Page<Job> findByCustomerIdAndStatusAndCertificateTypeOrderByCreatedAtDesc(
      UUID customerId, String status, String certificateType, Pageable pageable);

  // ── Engineer list queries ──────────────────────────────────────────────────
  Page<Job> findByEngineerIdOrderByCreatedAtDesc(UUID engineerId, Pageable pageable);

  Page<Job> findByEngineerIdAndStatusOrderByCreatedAtDesc(
      UUID engineerId, String status, Pageable pageable);

  Page<Job> findByEngineerIdAndCertificateTypeOrderByCreatedAtDesc(
      UUID engineerId, String certificateType, Pageable pageable);

  Page<Job> findByEngineerIdAndStatusAndCertificateTypeOrderByCreatedAtDesc(
      UUID engineerId, String status, String certificateType, Pageable pageable);

  // ── Admin list queries (all jobs with optional filters) ───────────────────
  @Query(
      "SELECT j FROM Job j WHERE "
          + "(:status IS NULL OR j.status = :status) AND "
          + "(:certificateType IS NULL OR j.certificateType = :certificateType) "
          + "ORDER BY j.createdAt DESC")
  Page<Job> findAllWithFilters(
      @Param("status") String status,
      @Param("certificateType") String certificateType,
      Pageable pageable);

  // ── Lookups ────────────────────────────────────────────────────────────────
  Optional<Job> findByReferenceNumber(String referenceNumber);

  // ── Validation: prevent deactivating a property with active jobs ──────────
  @Query(
      "SELECT CASE WHEN COUNT(j) > 0 THEN true ELSE false END FROM Job j "
          + "WHERE j.property.id = :propertyId AND j.status NOT IN :terminalStatuses")
  boolean existsByPropertyIdAndStatusNotIn(
      @Param("propertyId") UUID propertyId,
      @Param("terminalStatuses") List<String> terminalStatuses);

  // ── Matching Engine queries ─────────────────────────────────────────────

  /**
   * Loads a job with its {@code property} association eagerly joined so that the property can be
   * accessed after the loading transaction has closed (e.g. in the matching scheduler).
   */
  @Query("SELECT j FROM Job j JOIN FETCH j.property WHERE j.id = :id")
  Optional<Job> findByIdWithProperty(@Param("id") UUID id);

  /** Jobs in CREATED status that have not been broadcast yet (safety net for missed events). */
  List<Job> findByStatusAndBroadcastAtIsNull(String status);

  /** Jobs in AWAITING_ACCEPTANCE that were broadcast before the given cutoff time. */
  List<Job> findByStatusAndBroadcastAtBefore(String status, OffsetDateTime cutoff);

  /**
   * Atomic claim: conditionally update job to MATCHED only if it is currently AWAITING_ACCEPTANCE.
   * Returns the number of rows updated (1 = success, 0 = already claimed).
   */
  @Modifying
  @Query(
      "UPDATE Job j SET j.status = 'MATCHED', j.engineer = :engineer, "
          + "j.matchedAt = :now, j.updatedAt = :now, j.matchAttempts = j.matchAttempts + 1 "
          + "WHERE j.id = :jobId AND j.status = 'AWAITING_ACCEPTANCE'")
  int claimJob(
      @Param("jobId") UUID jobId,
      @Param("engineer") User engineer,
      @Param("now") OffsetDateTime now);

  /** Count jobs assigned to an engineer today (for daily job cap check). */
  @Query(
      "SELECT COUNT(j) FROM Job j WHERE j.engineer.id = :engineerId "
          + "AND j.status NOT IN ('CANCELLED', 'FAILED') "
          + "AND j.matchedAt >= :startOfDay")
  long countEngineerJobsToday(
      @Param("engineerId") UUID engineerId, @Param("startOfDay") OffsetDateTime startOfDay);
}
