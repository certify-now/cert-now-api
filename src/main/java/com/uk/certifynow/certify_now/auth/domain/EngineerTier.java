package com.uk.certifynow.certify_now.auth.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Enum representing engineer tier levels in the CertifyNow marketplace. Encapsulates tier-specific
 * business rules and default values.
 */
public enum EngineerTier {
  BRONZE("BRONZE", 5.0, 6),
  SILVER("SILVER", 10.0, 8),
  GOLD("GOLD", 15.0, 10),
  PLATINUM("PLATINUM", 25.0, 15);

  private final String databaseValue;
  private final double defaultServiceRadiusMiles;
  private final int defaultMaxDailyJobs;

  EngineerTier(
      final String databaseValue,
      final double defaultServiceRadiusMiles,
      final int defaultMaxDailyJobs) {
    this.databaseValue = databaseValue;
    this.defaultServiceRadiusMiles = defaultServiceRadiusMiles;
    this.defaultMaxDailyJobs = defaultMaxDailyJobs;
  }

  public String getDatabaseValue() {
    return databaseValue;
  }

  public double getDefaultServiceRadiusMiles() {
    return defaultServiceRadiusMiles;
  }

  public int getDefaultMaxDailyJobs() {
    return defaultMaxDailyJobs;
  }

  public static EngineerTier fromDatabaseValue(final String value) {
    if (value == null) {
      return null;
    }
    for (final EngineerTier tier : values()) {
      if (tier.databaseValue.equals(value)) {
        return tier;
      }
    }
    throw new IllegalArgumentException("Unknown EngineerTier database value: " + value);
  }

  /**
   * Determines if this tier is premium (Gold or Platinum).
   *
   * @return true if Gold or Platinum, false otherwise
   */
  public boolean isPremium() {
    return this == GOLD || this == PLATINUM;
  }

  /**
   * JPA AttributeConverter for automatic conversion between EngineerTier enum and String database
   * column.
   */
  @Converter(autoApply = true)
  public static class EngineerTierConverter implements AttributeConverter<EngineerTier, String> {

    @Override
    public String convertToDatabaseColumn(final EngineerTier attribute) {
      if (attribute == null) {
        return null;
      }
      return attribute.getDatabaseValue();
    }

    @Override
    public EngineerTier convertToEntityAttribute(final String dbData) {
      return EngineerTier.fromDatabaseValue(dbData);
    }
  }
}
