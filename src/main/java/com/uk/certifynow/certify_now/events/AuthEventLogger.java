package com.uk.certifynow.certify_now.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
    try {
      MDC.put("userId", event.getUserId().toString());
      MDC.put("eventType", "USER_REGISTERED");

      log.info(
          "User registered successfully | userId={} | email={} | role={} | authProvider={} | requiresVerification={}",
          event.getUserId(),
          maskEmail(event.getEmail()),
          event.getRole(),
          event.getAuthProvider() != null ? event.getAuthProvider() : "EMAIL",
          !event.isEmailVerified());

    } catch (Exception e) {
      log.error("Failed to log USER_REGISTERED event for userId={}", event.getUserId(), e);
    } finally {
      MDC.clear();
    }
  }

  @Async("authEventsExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserLoggedIn(final UserLoggedInEvent event) {
    try {
      MDC.put("userId", event.getUserId().toString());
      MDC.put("eventType", "USER_LOGGED_IN");

      log.info(
          "User logged in | userId={} | email={} | role={} | deviceInfo={} | ipAddress={} | timeSinceLastLogin={}",
          event.getUserId(),
          maskEmail(event.getEmail()),
          event.getRole() != null ? event.getRole() : "UNKNOWN",
          event.getDeviceInfo() != null ? event.getDeviceInfo() : "UNKNOWN",
          event.getIpAddress() != null ? maskIpAddress(event.getIpAddress()) : "UNKNOWN",
          calculateTimeSinceLastLogin(event.getLastLoginAt()));

    } catch (Exception e) {
      log.error("Failed to log USER_LOGGED_IN event for userId={}", event.getUserId(), e);
    } finally {
      MDC.clear();
    }
  }

  @Async("authEventsExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserLoggedOut(final UserLoggedOutEvent event) {
    try {
      MDC.put("userId", event.getUserId().toString());
      MDC.put("eventType", "USER_LOGGED_OUT");

      log.info(
          "User logged out | userId={} | sessionDuration={} | deviceInfo={}",
          event.getUserId(),
          formatDuration(event.getSessionDurationSeconds()),
          event.getDeviceInfo() != null ? event.getDeviceInfo() : "UNKNOWN");

    } catch (Exception e) {
      log.error("Failed to log USER_LOGGED_OUT event", e);
    } finally {
      MDC.clear();
    }
  }

  @Async("authEventsExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onLoginFailed(final LoginFailedEvent event) {
    try {
      MDC.put("eventType", "LOGIN_FAILED");

      log.warn(
          "Login attempt failed | email={} | reason={} | ipAddress={}",
          maskEmail(event.getEmail()),
          event.getReason(),
          maskIpAddress(event.getIpAddress()));

    } catch (Exception e) {
      log.error("Failed to log LOGIN_FAILED event", e);
    } finally {
      MDC.clear();
    }
  }

  @Async("authEventsExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onAccountDeactivated(final AccountDeactivatedEvent event) {
    try {
      MDC.put("userId", event.getUserId().toString());
      MDC.put("eventType", "ACCOUNT_DEACTIVATED");

      log.info(
          "Account deactivated | userId={} | email={} | reason={} | initiatedBy={}",
          event.getUserId(),
          maskEmail(event.getEmail()),
          event.getReason(),
          event.getInitiatedBy());

    } catch (Exception e) {
      log.error("Failed to log ACCOUNT_DEACTIVATED event", e);
    } finally {
      MDC.clear();
    }
  }

  @Async("authEventsExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onEmailVerified(final EmailVerifiedEvent event) {
    try {
      MDC.put("userId", event.getUserId().toString());
      MDC.put("eventType", "EMAIL_VERIFIED");

      log.info(
          "Email verified | userId={} | email={} | timeSinceRegistration={}",
          event.getUserId(),
          maskEmail(event.getEmail()),
          formatDuration(event.getSecondsSinceRegistration()));

    } catch (Exception e) {
      log.error("Failed to log EMAIL_VERIFIED event", e);
    } finally {
      MDC.clear();
    }
  }

  // ============================================================================
  // HELPER METHODS
  // ============================================================================

  /**
   * Masks email for GDPR compliance while keeping it searchable. Example: john.doe@example.com →
   * j***e@example.com
   */
  private String maskEmail(final String email) {
    if (email == null || email.length() < 3) {
      return "***";
    }

    final String[] parts = email.split("@");
    if (parts.length != 2) {
      return "***";
    }

    final String localPart = parts[0];
    final String domain = parts[1];

    if (localPart.length() <= 2) {
      return localPart.charAt(0) + "***@" + domain;
    }

    return localPart.charAt(0) + "***" + localPart.charAt(localPart.length() - 1) + "@" + domain;
  }

  /**
   * Masks IP address for privacy while keeping subnet identifiable. Example: 192.168.1.100 →
   * 192.168.***.***
   */
  private String maskIpAddress(final String ipAddress) {
    if (ipAddress == null) {
      return "UNKNOWN";
    }

    // IPv4
    if (ipAddress.contains(".")) {
      final String[] parts = ipAddress.split("\\.");
      if (parts.length == 4) {
        return parts[0] + "." + parts[1] + ".***.***";
      }
    }

    // IPv6 - just mask everything after first 2 segments
    if (ipAddress.contains(":")) {
      final String[] parts = ipAddress.split(":");
      if (parts.length >= 2) {
        return parts[0] + ":" + parts[1] + ":***";
      }
    }

    return "***";
  }

  /** Calculate human-readable time since last login. */
  private String calculateTimeSinceLastLogin(final java.time.OffsetDateTime lastLoginAt) {
    if (lastLoginAt == null) {
      return "FIRST_LOGIN";
    }

    final long seconds =
        java.time.Duration.between(lastLoginAt, java.time.OffsetDateTime.now()).getSeconds();
    return formatDuration(seconds);
  }

  /** Format duration in human-readable format. */
  private String formatDuration(final Long seconds) {
    if (seconds == null) {
      return "UNKNOWN";
    }

    if (seconds < 60) {
      return seconds + "s";
    } else if (seconds < 3600) {
      return (seconds / 60) + "m";
    } else if (seconds < 86400) {
      return (seconds / 3600) + "h";
    } else {
      return (seconds / 86400) + "d";
    }
  }
}
