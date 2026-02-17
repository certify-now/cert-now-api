package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.Payment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  Payment findFirstByCustomerId(UUID id);

  Payment findFirstByJobId(UUID id);
}
