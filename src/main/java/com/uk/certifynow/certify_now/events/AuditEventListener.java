package com.uk.certifynow.certify_now.events;

import com.uk.certifynow.certify_now.repos.AuditLogRepository;
import com.uk.certifynow.certify_now.service.AuditHelper;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tier 2 audit listener — writes {@code AuditLog} entries for domain events that are published
 * within service-layer transactions. Each handler runs asynchronously after the originating
 * transaction commits.
 */
@Component
public class AuditEventListener {

  private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

  private final AuditLogRepository auditLogRepository;
  private final Clock clock;

  public AuditEventListener(final AuditLogRepository auditLogRepository, final Clock clock) {
    this.auditLogRepository = auditLogRepository;
    this.clock = clock;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserSoftDeleted(final UserSoftDeletedEvent event) {
    try {
      auditLogRepository.save(
          AuditHelper.build(
              clock,
              event.getDeletedBy(),
              event.getActorType(),
              "USER_SOFT_DELETED",
              "User",
              event.getUserId(),
              null,
              null));
    } catch (final Exception e) {
      log.error("Failed to write audit log for USER_SOFT_DELETED userId={}", event.getUserId(), e);
    }
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserRestored(final UserRestoredEvent event) {
    try {
      auditLogRepository.save(
          AuditHelper.build(
              clock,
              event.getRestoredBy(),
              event.getActorType(),
              "USER_RESTORED",
              "User",
              event.getUserId(),
              null,
              null));
    } catch (final Exception e) {
      log.error("Failed to write audit log for USER_RESTORED userId={}", event.getUserId(), e);
    }
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPropertySoftDeleted(final PropertySoftDeletedEvent event) {
    try {
      auditLogRepository.save(
          AuditHelper.build(
              clock,
              event.getDeletedBy(),
              event.getActorType(),
              "PROPERTY_SOFT_DELETED",
              "Property",
              event.getPropertyId(),
              null,
              null));
    } catch (final Exception e) {
      log.error(
          "Failed to write audit log for PROPERTY_SOFT_DELETED propertyId={}",
          event.getPropertyId(),
          e);
    }
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPropertyRestored(final PropertyRestoredEvent event) {
    try {
      auditLogRepository.save(
          AuditHelper.build(
              clock,
              event.getRestoredBy(),
              event.getActorType(),
              "PROPERTY_RESTORED",
              "Property",
              event.getPropertyId(),
              null,
              null));
    } catch (final Exception e) {
      log.error(
          "Failed to write audit log for PROPERTY_RESTORED propertyId={}",
          event.getPropertyId(),
          e);
    }
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onEngineerApproved(final EngineerApprovedEvent event) {
    try {
      auditLogRepository.save(
          AuditHelper.build(
              clock,
              event.getApprovedBy(),
              event.getActorType(),
              "ENGINEER_APPROVED",
              "EngineerProfile",
              event.getEngineerProfileId(),
              null,
              null));
    } catch (final Exception e) {
      log.error(
          "Failed to write audit log for ENGINEER_APPROVED profileId={}",
          event.getEngineerProfileId(),
          e);
    }
  }
}
