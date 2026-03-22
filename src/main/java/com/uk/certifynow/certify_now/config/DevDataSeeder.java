package com.uk.certifynow.certify_now.config;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds fixed dev users into the database on startup (dev profile only).
 *
 * <p>Safe to run on every boot — each INSERT is guarded by a NOT EXISTS check. Wipe the DB and
 * restart the API; the users come back automatically.
 *
 * <pre>
 *   CUSTOMER  00000000-0000-0000-0000-000000000001  dev-customer@certifynow.local
 *   ENGINEER  00000000-0000-0000-0000-000000000002  dev-engineer@certifynow.local
 *   ADMIN     00000000-0000-0000-0000-000000000003  dev-admin@certifynow.local
 *
 *   Password for all three: Dev1234!
 * </pre>
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

  private static final String CUSTOMER_ID = DevAuthFilter.DEV_CUSTOMER_ID;
  private static final String ENGINEER_ID = DevAuthFilter.DEV_ENGINEER_ID;
  private static final String ADMIN_ID = DevAuthFilter.DEV_ADMIN_ID;

  private static final String CUSTOMER_PROFILE_ID = "00000000-0000-0000-0000-000000000011";
  private static final String ENGINEER_PROFILE_ID = "00000000-0000-0000-0000-000000000022";

  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwordEncoder;

  public DevDataSeeder(final JdbcTemplate jdbc, final PasswordEncoder passwordEncoder) {
    this.jdbc = jdbc;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(final ApplicationArguments args) {
    final String hash = passwordEncoder.encode("Dev1234!");
    final Timestamp now = Timestamp.from(Instant.now());

    seedUser(CUSTOMER_ID, "dev-customer@certifynow.local", "Dev Customer", "CUSTOMER", hash, now);
    seedCustomerProfile(CUSTOMER_PROFILE_ID, CUSTOMER_ID, now);

    seedUser(ENGINEER_ID, "dev-engineer@certifynow.local", "Dev Engineer", "ENGINEER", hash, now);
    seedEngineerProfile(ENGINEER_PROFILE_ID, ENGINEER_ID, now);

    seedUser(ADMIN_ID, "dev-admin@certifynow.local", "Dev Admin", "ADMIN", hash, now);

    seedPricingRules(now);
    seedUrgencyMultipliers(now);

    log.info("[DEV] Dev seed users ready — password for all: Dev1234!");
  }

  private void seedUser(
      final String id,
      final String email,
      final String name,
      final String role,
      final String hash,
      final Timestamp now) {
    final int rows =
        jdbc.update(
            """
        INSERT INTO "user"
          (id, email, full_name, password_hash, role, status, auth_provider,
           email_verified, phone_verified, created_at, updated_at)
        SELECT ?, ?, ?, ?, ?, 'ACTIVE', 'EMAIL', true, false, ?, ?
        WHERE NOT EXISTS (SELECT 1 FROM "user" WHERE id = ?)
        """,
            UUID.fromString(id),
            email,
            name,
            hash,
            role,
            now,
            now,
            UUID.fromString(id));
    if (rows > 0) log.info("[DEV] Seeded {} user: {}", role, email);
  }

  private void seedCustomerProfile(
      final String profileId, final String userId, final Timestamp now) {
    jdbc.update(
        """
        INSERT INTO customer_profile
          (id, user_id, is_letting_agent, total_properties, notification_prefs,
           created_at, updated_at, date_created, last_updated)
        SELECT ?, ?, false, 0, '{}', ?, ?, ?, ?
        WHERE NOT EXISTS (SELECT 1 FROM customer_profile WHERE user_id = ?)
        """,
        UUID.fromString(profileId),
        UUID.fromString(userId),
        now,
        now,
        now,
        now,
        UUID.fromString(userId));
  }

  private void seedEngineerProfile(
      final String profileId, final String userId, final Timestamp now) {
    jdbc.update(
        """
        INSERT INTO engineer_profile
          (id, user_id, status, tier, acceptance_rate, avg_rating,
           is_online, max_daily_jobs, on_time_percentage, service_radius_miles,
           stripe_onboarded, total_jobs_completed, total_reviews, created_at, updated_at)
        SELECT ?, ?, 'APPROVED', 'GOLD', ?, ?, true, 10, ?, ?, false, 0, 0, ?, ?
        WHERE NOT EXISTS (SELECT 1 FROM engineer_profile WHERE user_id = ?)
        """,
        UUID.fromString(profileId),
        UUID.fromString(userId),
        new BigDecimal("100.00"),
        new BigDecimal("5.00"),
        new BigDecimal("100.00"),
        new BigDecimal("15.0"),
        now,
        now,
        UUID.fromString(userId));
  }

  // ── Pricing ────────────────────────────────────────────────

  private static final Object[][] PRICING_RULES = {
    // { id, certificateType, basePricePence }
    {"a0000000-0000-0000-0000-000000000001", "GAS_SAFETY", 6500},
    {"a0000000-0000-0000-0000-000000000002", "EICR", 15000},
    {"a0000000-0000-0000-0000-000000000003", "EPC", 7500},
    {"a0000000-0000-0000-0000-000000000004", "PAT", 200},
    {"a0000000-0000-0000-0000-000000000005", "BOILER_SERVICE", 8000},
  };

  private static final Object[][] URGENCY_MULTIPLIERS = {
    // { id, urgency, multiplier }
    {"b0000000-0000-0000-0000-000000000001", "STANDARD", new BigDecimal("1.000")},
    {"b0000000-0000-0000-0000-000000000004", "PRIORITY", new BigDecimal("1.250")},
    {"b0000000-0000-0000-0000-000000000003", "EMERGENCY", new BigDecimal("1.500")},
  };

  private void seedPricingRules(final Timestamp now) {
    for (final Object[] row : PRICING_RULES) {
      final UUID id = UUID.fromString((String) row[0]);
      final int inserted =
          jdbc.update(
              """
          INSERT INTO pricing_rule
            (id, certificate_type, region, base_price_pence, is_active,
             effective_from, effective_to, created_at, date_created, last_updated)
          SELECT ?, ?, NULL, ?, true, '2024-01-01', NULL, ?, ?, ?
          WHERE NOT EXISTS (SELECT 1 FROM pricing_rule WHERE id = ?)
          """,
              id,
              row[1],
              row[2],
              now,
              now,
              now,
              id);
      if (inserted > 0) log.info("[DEV] Seeded pricing rule: {}", row[1]);
    }
  }

  private void seedUrgencyMultipliers(final Timestamp now) {
    for (final Object[] row : URGENCY_MULTIPLIERS) {
      final UUID id = UUID.fromString((String) row[0]);
      final int inserted =
          jdbc.update(
              """
          INSERT INTO urgency_multiplier
            (id, urgency, multiplier, is_active,
             effective_from, created_at, date_created, last_updated)
          SELECT ?, ?, ?, true, '2024-01-01', ?, ?, ?
          WHERE NOT EXISTS (SELECT 1 FROM urgency_multiplier WHERE id = ?)
          """,
              id,
              row[1],
              row[2],
              now,
              now,
              now,
              id);
      if (inserted > 0) log.info("[DEV] Seeded urgency multiplier: {}", row[1]);
    }
  }
}
