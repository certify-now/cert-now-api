package com.uk.certifynow.certify_now.service.auth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Enum representing authentication providers supported by CertifyNow. Designed to support future
 * OAuth integration.
 */
public enum AuthProvider {
  EMAIL("EMAIL"),
  GOOGLE("GOOGLE"),
  APPLE("APPLE"),
  MICROSOFT("MICROSOFT");

  private final String databaseValue;

  AuthProvider(final String databaseValue) {
    this.databaseValue = databaseValue;
  }

  public String getDatabaseValue() {
    return databaseValue;
  }

  public static AuthProvider fromDatabaseValue(final String value) {
    if (value == null) {
      return null;
    }
    for (final AuthProvider provider : values()) {
      if (provider.databaseValue.equals(value)) {
        return provider;
      }
    }
    throw new IllegalArgumentException("Unknown AuthProvider database value: " + value);
  }

  /**
   * Determines if this provider requires a password.
   *
   * @return true if password is required (EMAIL), false for OAuth providers
   */
  public boolean requiresPassword() {
    return this == EMAIL;
  }

  /**
   * Determines if this is an OAuth provider.
   *
   * @return true for OAuth providers (Google, Apple, Microsoft), false for EMAIL
   */
  public boolean isOAuth() {
    return this != EMAIL;
  }

  /**
   * JPA AttributeConverter for automatic conversion between AuthProvider enum and String database
   * column.
   */
  @Converter(autoApply = true)
  public static class AuthProviderConverter implements AttributeConverter<AuthProvider, String> {

    @Override
    public String convertToDatabaseColumn(final AuthProvider attribute) {
      if (attribute == null) {
        return null;
      }
      return attribute.getDatabaseValue();
    }

    @Override
    public AuthProvider convertToEntityAttribute(final String dbData) {
      return AuthProvider.fromDatabaseValue(dbData);
    }
  }
}
