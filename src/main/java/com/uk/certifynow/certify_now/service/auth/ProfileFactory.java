package com.uk.certifynow.certify_now.service.auth;

import com.uk.certifynow.certify_now.domain.CustomerProfile;
import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.domain.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/**
 * Factory for creating profile entities with proper initialization. Encapsulates profile creation
 * logic and business defaults.
 */
@Component
public class ProfileFactory {

  private static final String DEFAULT_NOTIFICATION_PREFS =
      "{\"push\": true, \"email\": true, \"sms\": false, \"reminderDays\": [90, 60, 30]}";

  private final Clock clock;

  public ProfileFactory(final Clock clock) {
    this.clock = clock;
  }

  /**
   * Creates a new CustomerProfile with default settings.
   *
   * @param user the user this profile belongs to
   * @return newly created CustomerProfile entity
   */
  public CustomerProfile createCustomerProfile(final User user) {
    final CustomerProfile profile = new CustomerProfile();
    profile.setUser(user);

    final OffsetDateTime now = OffsetDateTime.now(clock);
    profile.setCreatedAt(now);
    profile.setUpdatedAt(now);

    // Business defaults
    profile.setIsLettingAgent(false);
    profile.setTotalProperties(0);
    profile.setNotificationPrefs(DEFAULT_NOTIFICATION_PREFS);

    return profile;
  }

  /**
   * Creates a new EngineerProfile with default settings based on BRONZE tier. Business defaults are
   * encapsulated in the EngineerTier enum.
   *
   * @param user the user this profile belongs to
   * @return newly created EngineerProfile entity
   */
  public EngineerProfile createEngineerProfile(final User user) {
    final EngineerProfile profile = new EngineerProfile();
    profile.setUser(user);

    final OffsetDateTime now = OffsetDateTime.now(clock);
    profile.setCreatedAt(now);
    profile.setUpdatedAt(now);

    // Application status
    profile.setStatus(EngineerApplicationStatus.APPLICATION_SUBMITTED);

    // Tier and tier-specific defaults
    final EngineerTier defaultTier = EngineerTier.BRONZE;
    profile.setTier(defaultTier);
    profile.setServiceRadiusMiles(
        new BigDecimal(String.valueOf(defaultTier.getDefaultServiceRadiusMiles())));
    profile.setMaxDailyJobs(defaultTier.getDefaultMaxDailyJobs());

    // Performance metrics (initial values)
    profile.setIsOnline(false);
    profile.setAvgRating(BigDecimal.ZERO);
    profile.setTotalReviews(0);
    profile.setTotalJobsCompleted(0);
    profile.setOnTimePercentage(new BigDecimal("100.00"));
    profile.setAcceptanceRate(new BigDecimal("100.00"));

    // Payment integration
    profile.setStripeOnboarded(false);

    // Preferences
    profile.setPreferredCertTypes("{}");

    return profile;
  }
}
