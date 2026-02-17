package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.Payout;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

  Payout findFirstByEngineerId(UUID id);

  Payout findFirstByJobId(UUID id);

  Payout findFirstByPaymentId(UUID id);
}
