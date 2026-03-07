package com.uk.certifynow.certify_now.service.job;

/** Job urgency levels. Stored as a String in the database. */
public enum JobUrgency {
  STANDARD,
  PRIORITY,
  EMERGENCY;

  public static JobUrgency fromString(final String value) {
    if (value == null) {
      return STANDARD;
    }
    try {
      return JobUrgency.valueOf(value.toUpperCase());
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown JobUrgency: " + value);
    }
  }
}
