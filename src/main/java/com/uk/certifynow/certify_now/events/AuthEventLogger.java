package com.uk.certifynow.certify_now.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuthEventLogger {

  private static final Logger log = LoggerFactory.getLogger(AuthEventLogger.class);

  @Async("authEventsExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserRegistered(final UserRegisteredEvent event) {
    log.info(
        "USER_REGISTERED: userId={}, email={}, role={}",
        event.getUserId(),
        event.getEmail(),
        event.getRole());
  }

  @Async("authEventsExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserLoggedIn(final UserLoggedInEvent event) {
    log.info("USER_LOGGED_IN: userId={}, email={}", event.getUserId(), event.getEmail());
  }
}
