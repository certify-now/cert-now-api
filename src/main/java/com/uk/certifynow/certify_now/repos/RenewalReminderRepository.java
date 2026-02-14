package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.RenewalReminder;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;


public interface RenewalReminderRepository extends JpaRepository<RenewalReminder, UUID> {

    RenewalReminder findFirstByCertificateId(UUID id);

    RenewalReminder findFirstByCustomerId(UUID id);

    RenewalReminder findFirstByNotificationId(UUID id);

    RenewalReminder findFirstByPropertyId(UUID id);

}
