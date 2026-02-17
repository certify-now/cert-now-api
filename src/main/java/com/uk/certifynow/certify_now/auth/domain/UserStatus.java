package com.uk.certifynow.certify_now.auth.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Enum representing user account status in the CertifyNow platform. Encapsulates business rules for
 * account state validation.
 */
public enum UserStatus {
  ACTIVE("ACTIVE"),
  PENDING_VERIFICATION("PENDING_VERIFICATION"),
  SUSPENDED("SUSPENDED"),
  DEACTIVATED("DEACTIVATED");

  private final String databaseValue;

  UserStatus(final String databaseValue) {
    this.databaseValue = databaseValue;
  }

  public String getDatabaseValue() {
    return databaseValue;
  }

  public static UserStatus fromDatabaseValue(final String value) {
    if (value == null) {
      return null;
    }
    for (final UserStatus status : values()) {
      if (status.databaseValue.equals(value)) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unknown UserStatus database value: " + value);
  }

  /**
   * Determines if a user with this status can authenticate.
   *
   * @return true if user can login, false otherwise
   */
  public boolean canAuthenticate() {
    return this == ACTIVE || this == PENDING_VERIFICATION;
  }

  /**
   * Determines if a user with this status requires verification.
   *
   * @return true if verification is pending, false otherwise
   */
  public boolean requiresVerification() {
    return this == PENDING_VERIFICATION;
  }

  /**
   * Determines if the account is suspended.
   *
   * @return true if suspended, false otherwise
   */
  public boolean isSuspended() {
    return this == SUSPENDED;
  }

  /**
   * Determines if the account is deactivated.
   *
   * @return true if deactivated, false otherwise
   */
  public boolean isDeactivated() {
    return this == DEACTIVATED;
  }

  /**
   * JPA AttributeConverter for automatic conversion between UserStatus enum and String database
   * column.
   */
  @Converter(autoApply = true)
  public static class UserStatusConverter implements AttributeConverter<UserStatus, String> {

    @Override
    public String convertToDatabaseColumn(final UserStatus attribute) {
      if (attribute == null) {
        return null;
      }
      return attribute.getDatabaseValue();
    }

    @Override
    public UserStatus convertToEntityAttribute(final String dbData) {
      return UserStatus.fromDatabaseValue(dbData);
    }
  }
}
