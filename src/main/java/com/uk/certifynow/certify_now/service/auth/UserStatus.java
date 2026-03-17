package com.uk.certifynow.certify_now.service.auth;

import com.uk.certifynow.certify_now.exception.BusinessException;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.http.HttpStatus;

/**
 * Enum representing user account status in the CertifyNow platform. Encapsulates business rules for
 * account state validation.
 */
public enum UserStatus {
  ACTIVE,
  PENDING_VERIFICATION,
  SUSPENDED,
  DEACTIVATED;

  /**
   * Asserts that a user with this status is permitted to authenticate.
   *
   * <p>Throws a {@link BusinessException} for DEACTIVATED or SUSPENDED accounts so that every
   * authentication path (login, token refresh) uses a single, consistent set of error codes and
   * messages instead of duplicating the checks in each service.
   */
  public void assertCanAuthenticate() {
    if (this == DEACTIVATED) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED,
          "ACCOUNT_DEACTIVATED",
          "Your account has been deactivated. Please contact support.");
    }
    if (this == SUSPENDED) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "ACCOUNT_SUSPENDED",
          "Your account has been suspended. Please contact support.");
    }
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
      return attribute == null ? null : attribute.name();
    }

    @Override
    public UserStatus convertToEntityAttribute(final String dbData) {
      return dbData == null ? null : UserStatus.valueOf(dbData);
    }
  }
}
