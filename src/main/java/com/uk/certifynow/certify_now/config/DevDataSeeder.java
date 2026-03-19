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
 * <p>Safe to run on every boot — each INSERT is guarded by a NOT EXISTS check.
 * Wipe the DB and restart the API; the users come back automatically.
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
  private static final String ADMIN_ID    = DevAuthFilter.DEV_ADMIN_ID;

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

    log.info("[DEV] Dev seed users ready — password for all: Dev1234!");
  }

  private void seedUser(
      final String id, final String email, final String name,
      final String role, final String hash, final Timestamp now) {
    final int rows = jdbc.update("""
        INSERT INTO "user"
          (id, email, full_name, password_hash, role, status, auth_provider,
           email_verified, phone_verified, created_at, updated_at)
        SELECT ?, ?, ?, ?, ?, 'ACTIVE', 'EMAIL', true, false, ?, ?
        WHERE NOT EXISTS (SELECT 1 FROM "user" WHERE id = ?)
        """,
        UUID.fromString(id), email, name, hash, role, now, now, UUID.fromString(id));
    if (rows > 0) log.info("[DEV] Seeded {} user: {}", role, email);
  }

  private void seedCustomerProfile(final String profileId, final String userId, final Timestamp now) {
    jdbc.update("""
        INSERT INTO customer_profile
          (id, user_id, is_letting_agent, total_properties, notification_prefs,
           created_at, updated_at, date_created, last_updated)
        SELECT ?, ?, false, 0, '{}', ?, ?, ?, ?
        WHERE NOT EXISTS (SELECT 1 FROM customer_profile WHERE user_id = ?)
        """,
        UUID.fromString(profileId), UUID.fromString(userId), now, now, now, now,
        UUID.fromString(userId));
  }

  private void seedEngineerProfile(final String profileId, final String userId, final Timestamp now) {
    jdbc.update("""
        INSERT INTO engineer_profile
          (id, user_id, status, tier, acceptance_rate, avg_rating,
           is_online, max_daily_jobs, on_time_percentage, service_radius_miles,
           stripe_onboarded, total_jobs_completed, total_reviews, created_at, updated_at)
        SELECT ?, ?, 'APPROVED', 'GOLD', ?, ?, true, 10, ?, ?, false, 0, 0, ?, ?
        WHERE NOT EXISTS (SELECT 1 FROM engineer_profile WHERE user_id = ?)
        """,
        UUID.fromString(profileId), UUID.fromString(userId),
        new BigDecimal("100.00"), new BigDecimal("5.00"),
        new BigDecimal("100.00"), new BigDecimal("15.0"),
        now, now, UUID.fromString(userId));
  }
}
