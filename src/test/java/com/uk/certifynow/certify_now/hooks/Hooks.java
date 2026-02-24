package com.uk.certifynow.certify_now.hooks;

import static org.assertj.core.api.Assertions.assertThat;

import com.uk.certifynow.certify_now.util.TokenDenylistTestUtils;
import com.uk.certifynow.certify_now.util.WireMockUtils;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

public class Hooks {

  private static final Logger log = LoggerFactory.getLogger(Hooks.class);

  private final JdbcTemplate jdbcTemplate;
  private final TokenDenylistTestUtils tokenDenylistTestUtils;
  private final WireMockUtils wireMockUtils;

  public Hooks(
      final JdbcTemplate jdbcTemplate,
      final TokenDenylistTestUtils tokenDenylistTestUtils,
      final WireMockUtils wireMockUtils) {
    this.jdbcTemplate = jdbcTemplate;
    this.tokenDenylistTestUtils = tokenDenylistTestUtils;
    this.wireMockUtils = wireMockUtils;
  }

  @Before
  @Order(0)
  public void resetWireMockAndDenylist() {
    wireMockUtils.resetStubs();
    wireMockUtils.stubEmailSuccess();
    tokenDenylistTestUtils.clearAll();
  }

  @Before
  @Order(1)
  public void cleanDatabase() {
    truncateIfExists("user_consent");
    truncateIfExists("user_consents");
    truncateIfExists("email_verification_tokens");
    truncateIfExists("refresh_token");
    truncateIfExists("refresh_tokens");
    truncateIfExists("customer_profile");
    truncateIfExists("customer_profiles");
    truncateIfExists("engineer_profile");
    truncateIfExists("engineer_profiles");
    truncateIfExists("\"user\"");
    truncateIfExists("users");
  }

  @Before("@async")
  @Order(2)
  public void asyncTolerance() {
    Awaitility.setDefaultTimeout(Duration.ofSeconds(5));
    Awaitility.setDefaultPollInterval(Duration.ofMillis(200));
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> true);
  }

  @After
  @Order(999)
  public void afterScenario(final Scenario scenario) {
    final String status = scenario.isFailed() ? "FAILED" : "PASSED";
    log.info("Scenario '{}' finished with status {}", scenario.getName(), status);
  }

  private void truncateIfExists(final String tableName) {
    if (!tableExists(tableName.replace("\"", ""))) {
      return;
    }
    jdbcTemplate.execute("TRUNCATE TABLE " + tableName + " RESTART IDENTITY CASCADE");
  }

  private boolean tableExists(final String tableName) {
    final Integer count =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from information_schema.tables
            where table_schema = 'public'
              and table_name = ?
            """,
            Integer.class,
            tableName);
    assertThat(count).isNotNull();
    return count > 0;
  }
}
