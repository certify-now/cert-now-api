package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.Job;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
