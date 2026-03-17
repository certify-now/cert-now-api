package com.uk.certifynow.certify_now.scheduler;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.CustomerProfileRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * GDPR two-stage delete scheduler. Runs daily and anonymizes users that have been soft-deleted for
 * longer than the configured grace period (default 30 days).
 *
 * <p>Anonymization replaces PII with placeholder values while preserving the row for referential
 * integrity (payments, payouts, and jobs still reference the anonymized user).
 */
@Component
public class GdprDataPurgeScheduler {

  private static final Logger log = LoggerFactory.getLogger(GdprDataPurgeScheduler.class);

  private final UserRepository userRepository;
  private final CustomerProfileRepository customerProfileRepository;
  private final EngineerProfileRepository engineerProfileRepository;
  private final int gracePeriodDays;
  private final Clock clock;

  public GdprDataPurgeScheduler(
      final UserRepository userRepository,
      final CustomerProfileRepository customerProfileRepository,
      final EngineerProfileRepository engineerProfileRepository,
      @Value("${certifynow.gdpr.grace-period-days:30}") final int gracePeriodDays,
      final Clock clock) {
    this.userRepository = userRepository;
    this.customerProfileRepository = customerProfileRepository;
    this.engineerProfileRepository = engineerProfileRepository;
    this.gracePeriodDays = gracePeriodDays;
    this.clock = clock;
  }

  /** Runs daily at 2 AM UTC. Finds and anonymizes users past the grace period. */
  @Scheduled(cron = "0 0 2 * * *")
  @Transactional
  public void purgeExpiredSoftDeletedUsers() {
    final OffsetDateTime cutoff = OffsetDateTime.now(clock).minusDays(gracePeriodDays);
    final List<User> expiredUsers = userRepository.findAllDeletedBefore(cutoff);

    if (expiredUsers.isEmpty()) {
      log.debug("GDPR purge: no users past {}-day grace period", gracePeriodDays);
      return;
    }

    log.info(
        "GDPR purge: found {} users past {}-day grace period, starting anonymization",
        expiredUsers.size(),
        gracePeriodDays);

    for (final User user : expiredUsers) {
      try {
        anonymizeUser(user);
      } catch (final Exception e) {
        log.error("GDPR purge: failed to anonymize user {}", user.getId(), e);
      }
    }
  }

  /**
   * Anonymizes a single user and their associated profile. Replaces PII with placeholder values
   * while keeping the row intact for FK references.
   */
  void anonymizeUser(final User user) {
    final String anonymizedEmail = "deleted_" + user.getId() + "@deleted.certifynow.co.uk";

    user.setFullName("Deleted User");
    user.setEmail(anonymizedEmail);
    user.setPhone(null);
    user.setAvatarUrl(null);
    user.setExternalAuthId(null);
    user.setPasswordHash("ANONYMIZED");
    userRepository.save(user);

    // Anonymize associated profiles
    anonymizeCustomerProfile(user.getId());
    anonymizeEngineerProfile(user.getId());

    log.info("GDPR purge: anonymized user {}", user.getId());
  }

  private void anonymizeCustomerProfile(final java.util.UUID userId) {
    customerProfileRepository
        .findByUserIdIncludingDeleted(userId)
        .ifPresent(
            profile -> {
              // CustomerProfile has no standalone PII fields beyond the user FK —
              // no fields to clear, no save needed.
              log.debug("GDPR purge: customer profile checked for user {}", userId);
            });
  }

  private void anonymizeEngineerProfile(final java.util.UUID userId) {
    engineerProfileRepository
        .findByUserIdIncludingDeleted(userId)
        .ifPresent(
            profile -> {
              profile.setBio(null);
              profile.setLocation(null);
              profile.setIsOnline(false);
              engineerProfileRepository.save(profile);
              log.debug("GDPR purge: anonymized engineer profile for user {}", userId);
            });
  }
}
