package com.uk.certifynow.certify_now.service.job;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Generates human-readable job reference numbers in format: CN-YYYYMMDD-XXXX
 *
 * <p>Example: CN-20260214-A3K8
 *
 * <p>XXXX is a random 4-character uppercase alphanumeric. If a collision occurs (unique constraint
 * violation), the caller should retry — this generator always returns a fresh value.
 */
@Component
public class ReferenceNumberGenerator {

  private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private static final int SUFFIX_LENGTH = 4;
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
  private final SecureRandom random = new SecureRandom();

  public String generate() {
    final String date = LocalDate.now().format(DATE_FORMAT); // e.g. 20260214
    final String suffix = randomSuffix();
    return "CN-" + date + "-" + suffix;
  }

  private String randomSuffix() {
    final StringBuilder sb = new StringBuilder(SUFFIX_LENGTH);
    for (int i = 0; i < SUFFIX_LENGTH; i++) {
      sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
    }
    return sb.toString();
  }
}
