package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.Payment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  /** Find the payment for a given job (1:1 relationship). */
  Optional<Payment> findByJobId(UUID jobId);

  /** Used by CRUD stub (PaymentService BeforeDeleteUser listener). */
  Payment findFirstByCustomerId(UUID customerId);

  /** Used by CRUD stub (PaymentService BeforeDeleteJob listener). */
  Payment findFirstByJobId(UUID jobId);
}
