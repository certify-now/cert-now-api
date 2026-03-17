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
      // Set MDC for structured logging
      MDC.put("userId", event.getUserId().toString());
      MDC.put("email", event.getEmail());
      MDC.put("eventType", "USER_REGISTERED");

      log.info(
          "User registered successfully | userId={} | email={} | role={} | authProvider={} | requiresVerification={}",
          event.getUserId(),
          maskEmail(event.getEmail()),
          event.getRole(),
          event.getAuthProvider() != null ? event.getAuthProvider() : "EMAIL",
          !event.isEmailVerified());

      // Business metrics logging (for analytics/monitoring)
      if ("CUSTOMER".equals(event.getRole())) {
        log.info(
            "New customer acquisition | userId={} | source={}",
            event.getUserId(),
            event.getAuthProvider() != null ? event.getAuthProvider() : "EMAIL");
      } else if ("ENGINEER".equals(event.getRole())) {
        log.info(
            "New engineer registration | userId={} | requiresApproval=true", event.getUserId());
      }

    } catch (Exception e) {
      // Never let logging break the flow
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
      MDC.put("email", event.getEmail());
      MDC.put("eventType", "USER_LOGGED_IN");

      log.info(
          "User logged in | userId={} | email={} | role={} | deviceInfo={} | ipAddress={} | timeSinceLastLogin={}",
          event.getUserId(),
          maskEmail(event.getEmail()),
          event.getRole() != null ? event.getRole() : "UNKNOWN",
          event.getDeviceInfo() != null ? event.getDeviceInfo() : "UNKNOWN",
          event.getIpAddress() != null ? maskIpAddress(event.getIpAddress()) : "UNKNOWN",
          calculateTimeSinceLastLogin(event.getLastLoginAt()));

      // Security monitoring
      if (event.isNewDevice()) {
        log.warn(
            "Login from new device detected | userId={} | deviceInfo={} | ipAddress={}",
            event.getUserId(),
            event.getDeviceInfo(),
            maskIpAddress(event.getIpAddress()));
      }

      // Unusual activity detection
      if (event.getFailedAttempts() != null && event.getFailedAttempts() > 0) {
        log.warn(
            "Login after {} failed attempts | userId={} | ipAddress={}",
            event.getFailedAttempts(),
            event.getUserId(),
            maskIpAddress(event.getIpAddress()));
      }

      // User engagement metrics
      if (event.getDaysSinceLastLogin() != null && event.getDaysSinceLastLogin() > 30) {
        log.info(
            "Returning user after long absence | userId={} | daysSinceLastLogin={}",
            event.getUserId(),
            event.getDaysSinceLastLogin());
      }

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
      MDC.put("email", event.getEmail());
      MDC.put("eventType", "LOGIN_FAILED");

      log.warn(
          "Login attempt failed | email={} | reason={} | attemptCount={} | ipAddress={} | userAgent={}",
          maskEmail(event.getEmail()),
          event.getReason(),
          event.getAttemptCount(),
          maskIpAddress(event.getIpAddress()),
          truncate(event.getUserAgent(), 100));

      // Security alerts
      if (event.getAttemptCount() >= 3) {
        log.error(
            "SECURITY_ALERT: Multiple failed login attempts | email={} | attempts={} | ipAddress={}",
            maskEmail(event.getEmail()),
            event.getAttemptCount(),
            maskIpAddress(event.getIpAddress()));
      }

      if ("ACCOUNT_SUSPENDED".equals(event.getReason())) {
        log.warn(
            "Login attempt on suspended account | email={} | suspensionReason={}",
            maskEmail(event.getEmail()),
            event.getSuspensionReason());
      }

    } catch (Exception e) {
      log.error("Failed to log LOGIN_FAILED event", e);
    } finally {
      MDC.clear();
    }
  }

  @Async("authEventsExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPasswordResetRequested(final PasswordResetRequestedEvent event) {
    try {
      MDC.put("userId", event.getUserId().toString());
      MDC.put("eventType", "PASSWORD_RESET_REQUESTED");

      log.info(
          "Password reset requested | userId={} | email={} | ipAddress={}",
          event.getUserId(),
          maskEmail(event.getEmail()),
          maskIpAddress(event.getIpAddress()));

    } catch (Exception e) {
      log.error("Failed to log PASSWORD_RESET_REQUESTED event", e);
    } finally {
      MDC.clear();
    }
  }

  @Async("authEventsExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPasswordChanged(final PasswordChangedEvent event) {
    try {
      MDC.put("userId", event.getUserId().toString());
      MDC.put("eventType", "PASSWORD_CHANGED");

      log.info(
          "Password changed | userId={} | method={} | ipAddress={}",
          event.getUserId(),
          event.isPasswordReset() ? "RESET_TOKEN" : "AUTHENTICATED_CHANGE",
          maskIpAddress(event.getIpAddress()));

      // Security alert if not initiated by user
      if (!event.isUserInitiated()) {
        log.warn(
            "SECURITY_ALERT: Password changed without user initiation | userId={} | method={}",
            event.getUserId(),
            event.getMethod());
      }

    } catch (Exception e) {
      log.error("Failed to log PASSWORD_CHANGED event", e);
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
          event.getInitiatedBy() // USER or ADMIN
          );

      // Churn tracking
      if ("USER".equals(event.getInitiatedBy())) {
        log.info(
            "User-initiated churn | userId={} | accountAge={} | totalJobsCompleted={}",
            event.getUserId(),
            event.getAccountAgeInDays(),
            event.getTotalJobsCompleted());
      }

    } catch (Exception e) {
      log.error("Failed to log ACCOUNT_DEACTIVATED event", e);
    } finally {
      MDC.clear();
    }
  }

  @Async("authEventsExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onAccountSuspended(final AccountSuspendedEvent event) {
    try {
      MDC.put("userId", event.getUserId().toString());
      MDC.put("eventType", "ACCOUNT_SUSPENDED");

      log.warn(
          "ADMIN_ACTION: Account suspended | userId={} | email={} | reason={} | suspendedBy={} | duration={}",
          event.getUserId(),
          maskEmail(event.getEmail()),
          event.getReason(),
          event.getSuspendedByAdminId(),
          event.getSuspensionDuration() != null ? event.getSuspensionDuration() : "INDEFINITE");

    } catch (Exception e) {
      log.error("Failed to log ACCOUNT_SUSPENDED event", e);
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

      // Activation funnel tracking
      if (event.getSecondsSinceRegistration() != null
          && event.getSecondsSinceRegistration() < 300) {
        log.info("Fast email verification (< 5 min) | userId={}", event.getUserId());
      } else if (event.getSecondsSinceRegistration() != null
          && event.getSecondsSinceRegistration() > 86400) {
        log.info("Delayed email verification (> 24 hours) | userId={}", event.getUserId());
      }

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
        return parts[0] + "." + parts[1] + ".***. ***";
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

  /** Truncate long strings to prevent log pollution. */
  private String truncate(final String str, final int maxLength) {
    if (str == null) {
      return null;
    }
    return str.length() <= maxLength ? str : str.substring(0, maxLength) + "...";
  }
}
