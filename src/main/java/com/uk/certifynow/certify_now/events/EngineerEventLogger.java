package com.uk.certifynow.certify_now.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Stub event listeners that log all engineer domain events.
 *
 * <p>These will be replaced with real notification logic in Phase 9.
 */
@Component
public class EngineerEventLogger {

  private static final Logger log = LoggerFactory.getLogger(EngineerEventLogger.class);

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onEngineerApproved(final EngineerApprovedEvent event) {
    log.info(
        "ENGINEER_APPROVED: engineerUserId={}, profileId={}, approvedBy={}, approvedAt={}",
        event.getEngineerUserId(),
        event.getEngineerProfileId(),
        event.getApprovedBy(),
        event.getApprovedAt());
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onEngineerWentOnline(final EngineerWentOnlineEvent event) {
    log.info(
        "ENGINEER_WENT_ONLINE: engineerUserId={}, profileId={}",
        event.getEngineerUserId(),
        event.getEngineerProfileId());
  }
}
