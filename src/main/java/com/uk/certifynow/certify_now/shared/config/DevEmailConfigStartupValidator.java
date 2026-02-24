package com.uk.certifynow.certify_now.shared.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * Dev-only startup validation for email configuration.
 *
 * <p>Logs non-sensitive values and masked credentials to make local setup issues visible, and fails
 * fast when SMTP is selected but required values are missing.
 */
@Configuration
@Profile("dev")
public class DevEmailConfigStartupValidator {

  private static final Logger log = LoggerFactory.getLogger(DevEmailConfigStartupValidator.class);

  @Bean
  public ApplicationRunner validateDevEmailConfig(final Environment env) {
    return args -> {
      final String provider = get(env, "app.email.provider");
      final String from = get(env, "app.email.from");
      final String mailHost = get(env, "spring.mail.host");
      final String mailPort = get(env, "spring.mail.port");
      final String mailUsername = get(env, "spring.mail.username");
      final String mailPassword = get(env, "spring.mail.password");

      log.info("DEV email config: provider={}", safe(provider));
      log.info("DEV email config: from={}", safe(from));
      log.info("DEV email config: smtpHost={} smtpPort={}", safe(mailHost), safe(mailPort));
      log.info("DEV email config: smtpUsername={}", safe(mailUsername));
      log.info("DEV email config: smtpPassword={}", maskSecret(mailPassword));

      if (!"smtp".equalsIgnoreCase(provider)) {
        log.info("DEV email config check: provider is not smtp; SMTP credential checks skipped.");
        return;
      }

      requireHasText(from, "app.email.from");
      requireHasText(mailHost, "spring.mail.host");
      requireHasText(mailPort, "spring.mail.port");
      requireHasText(mailUsername, "spring.mail.username");
      requireHasText(mailPassword, "spring.mail.password");

      if (mailPassword.contains(" ")) {
        throw new IllegalStateException(
            "spring.mail.password appears to contain spaces. For Gmail app passwords, remove spaces.");
      }

      if (mailPassword.length() < 16) {
        throw new IllegalStateException(
            "spring.mail.password is unexpectedly short. For Gmail app passwords, use the 16-character value.");
      }

      log.info("DEV email config check: SMTP configuration looks valid.");
    };
  }

  private String get(final Environment env, final String key) {
    return env.getProperty(key);
  }

  private void requireHasText(final String value, final String key) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing required property for dev SMTP: " + key);
    }
  }

  private String safe(final String value) {
    return (value == null || value.isBlank()) ? "<blank>" : value;
  }

  private String maskSecret(final String value) {
    if (value == null || value.isBlank()) {
      return "<blank>";
    }
    if (value.length() <= 4) {
      return "***";
    }
    return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
  }
}
