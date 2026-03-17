package com.uk.certifynow.certify_now.service.auth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Enum representing authentication providers supported by CertifyNow. */
public enum AuthProvider {
  EMAIL,
  GOOGLE,
  APPLE,
  MICROSOFT;

  /**
   * Determines if this provider requires a password.
   *
   * <p>Used to guard password-based authentication so that OAuth users cannot authenticate via the
   * email/password login path.
   *
   * @return true if password is required (EMAIL only), false for OAuth providers
   */
  public boolean requiresPassword() {
    return this == EMAIL;
  }

  /**
   * JPA AttributeConverter for automatic conversion between AuthProvider enum and String database
   * column.
   */
  @Converter(autoApply = true)
  public static class AuthProviderConverter implements AttributeConverter<AuthProvider, String> {

    @Override
    public String convertToDatabaseColumn(final AuthProvider attribute) {
      return attribute == null ? null : attribute.name();
    }

    @Override
    public AuthProvider convertToEntityAttribute(final String dbData) {
      return dbData == null ? null : AuthProvider.valueOf(dbData);
    }
  }
}
