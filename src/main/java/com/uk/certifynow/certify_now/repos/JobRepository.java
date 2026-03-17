package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.User;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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
  // EntityGraph is used instead of JOIN FETCH to avoid Hibernate's HHH90003004
  // "firstResult/maxResults specified with collection fetch; applying in memory" warning.
  @EntityGraph(attributePaths = {"property", "engineer"})
  @Query(
      value =
          "SELECT j FROM Job j "
              + "WHERE j.customer.id = :customerId "
              + "AND (:statuses IS EMPTY OR j.status IN :statuses) "
              + "AND (:certificateType IS NULL OR j.certificateType = :certificateType) "
              + "ORDER BY j.createdAt DESC",
      countQuery =
          "SELECT COUNT(j) FROM Job j "
              + "WHERE j.customer.id = :customerId "
              + "AND (:statuses IS EMPTY OR j.status IN :statuses) "
              + "AND (:certificateType IS NULL OR j.certificateType = :certificateType)")
  Page<Job> findByCustomerWithFilters(
      @Param("customerId") UUID customerId,
      @Param("statuses") List<String> statuses,
      @Param("certificateType") String certificateType,
      Pageable pageable);

  // ── Engineer list queries ──────────────────────────────────────────────────
  @EntityGraph(attributePaths = {"property", "engineer"})
  @Query(
      value =
          "SELECT j FROM Job j "
              + "WHERE j.engineer.id = :engineerId "
              + "AND (:statuses IS EMPTY OR j.status IN :statuses) "
              + "AND (:certificateType IS NULL OR j.certificateType = :certificateType) "
              + "ORDER BY j.createdAt DESC",
      countQuery =
          "SELECT COUNT(j) FROM Job j "
              + "WHERE j.engineer.id = :engineerId "
              + "AND (:statuses IS EMPTY OR j.status IN :statuses) "
              + "AND (:certificateType IS NULL OR j.certificateType = :certificateType)")
  Page<Job> findByEngineerWithFilters(
      @Param("engineerId") UUID engineerId,
      @Param("statuses") List<String> statuses,
      @Param("certificateType") String certificateType,
      Pageable pageable);

  // ── Admin list queries (all jobs with optional filters) ───────────────────
  @Query(
      value =
          "SELECT j FROM Job j WHERE "
              + "(:statuses IS EMPTY OR j.status IN :statuses) AND "
              + "(:certificateType IS NULL OR j.certificateType = :certificateType) "
              + "ORDER BY j.createdAt DESC",
      countQuery =
          "SELECT COUNT(j) FROM Job j WHERE "
              + "(:statuses IS EMPTY OR j.status IN :statuses) AND "
              + "(:certificateType IS NULL OR j.certificateType = :certificateType)")
  Page<Job> findAllWithFilters(
      @Param("statuses") List<String> statuses,
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
      @Param("terminalStatuses") Collection<String> terminalStatuses);

  // ── Soft-delete validation: check for active (non-terminal) jobs ───────────

  @Query(
      "SELECT CASE WHEN COUNT(j) > 0 THEN true ELSE false END FROM Job j "
          + "WHERE j.customer.id = :userId AND j.status NOT IN :terminalStatuses")
  boolean existsActiveJobsByCustomerId(
      @Param("userId") UUID userId, @Param("terminalStatuses") Collection<String> terminalStatuses);

  @Query(
      "SELECT CASE WHEN COUNT(j) > 0 THEN true ELSE false END FROM Job j "
          + "WHERE j.engineer.id = :userId AND j.status NOT IN :terminalStatuses")
  boolean existsActiveJobsByEngineerId(
      @Param("userId") UUID userId, @Param("terminalStatuses") Collection<String> terminalStatuses);

  // ── Matching Engine queries ─────────────────────────────────────────────

  /**
   * Loads a job with its {@code property} association eagerly joined so that the property can be
   * accessed after the loading transaction has closed (e.g. in the matching scheduler).
   */
  @Query("SELECT j FROM Job j JOIN FETCH j.property WHERE j.id = :id")
  Optional<Job> findByIdWithProperty(@Param("id") UUID id);

  /** Jobs in CREATED status that have not been broadcast yet — paginated batch variant. */
  Page<Job> findByStatusAndBroadcastAtIsNull(String status, Pageable pageable);

  /**
   * Jobs in AWAITING_ACCEPTANCE that were broadcast before the given cutoff — paginated batch
   * variant.
   */
  Page<Job> findByStatusAndBroadcastAtBefore(
      String status, OffsetDateTime cutoff, Pageable pageable);

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

  /** Batch version: count today's jobs for multiple engineers in a single query. */
  @Query(
      "SELECT j.engineer.id, COUNT(j) FROM Job j "
          + "WHERE j.engineer.id IN :engineerIds "
          + "AND j.status NOT IN ('CANCELLED', 'FAILED') "
          + "AND j.matchedAt >= :startOfDay "
          + "GROUP BY j.engineer.id")
  List<Object[]> countEngineerJobsTodayBatch(
      @Param("engineerIds") List<UUID> engineerIds, @Param("startOfDay") OffsetDateTime startOfDay);
}
