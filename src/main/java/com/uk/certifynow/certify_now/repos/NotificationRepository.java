package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.Notification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  Notification findFirstByRelatedJobId(UUID id);

  Notification findFirstByUserId(UUID id);
}
