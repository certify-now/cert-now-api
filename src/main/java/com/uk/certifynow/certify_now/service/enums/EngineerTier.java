package com.uk.certifynow.certify_now.service.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Enum representing engineer tier levels in the CertifyNow marketplace. Encapsulates tier-specific
 * business rules and default values.
 */
public enum EngineerTier {
  BRONZE(5.0, 6),
  SILVER(10.0, 8),
  GOLD(15.0, 10),
  PLATINUM(25.0, 15);

  private final double defaultServiceRadiusMiles;
  private final int defaultMaxDailyJobs;

  EngineerTier(final double defaultServiceRadiusMiles, final int defaultMaxDailyJobs) {
    this.defaultServiceRadiusMiles = defaultServiceRadiusMiles;
    this.defaultMaxDailyJobs = defaultMaxDailyJobs;
  }

  public double getDefaultServiceRadiusMiles() {
    return defaultServiceRadiusMiles;
  }

  public int getDefaultMaxDailyJobs() {
    return defaultMaxDailyJobs;
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
      return attribute == null ? null : attribute.name();
    }

    @Override
    public EngineerTier convertToEntityAttribute(final String dbData) {
      return dbData == null ? null : EngineerTier.valueOf(dbData);
    }
  }
}
