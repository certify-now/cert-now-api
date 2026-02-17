package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.Message;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {

  Message findFirstByJobId(UUID id);

  Message findFirstBySenderId(UUID id);
}
