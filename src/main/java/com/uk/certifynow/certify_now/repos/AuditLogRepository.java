package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.AuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {}
