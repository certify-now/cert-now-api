package com.uk.certifynow.certify_now.service.auth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Enum representing user roles in the CertifyNow platform. Uses JPA AttributeConverter for backward
 * compatibility with existing string-based database values.
 */
public enum UserRole {
  CUSTOMER("CUSTOMER"),
  ENGINEER("ENGINEER"),
  ADMIN("ADMIN");

  private final String databaseValue;

  UserRole(final String databaseValue) {
    this.databaseValue = databaseValue;
  }

  public String getDatabaseValue() {
    return databaseValue;
  }

  public static UserRole fromDatabaseValue(final String value) {
    if (value == null) {
      return null;
    }
    for (final UserRole role : values()) {
      if (role.databaseValue.equals(value)) {
        return role;
      }
    }
    throw new IllegalArgumentException("Unknown UserRole database value: " + value);
  }

  public boolean isEngineer() {
    return this == ENGINEER;
  }

  public boolean isCustomer() {
    return this == CUSTOMER;
  }

  public boolean isAdmin() {
    return this == ADMIN;
  }

  /**
   * JPA AttributeConverter for automatic conversion between UserRole enum and String database
   * column. This ensures backward compatibility with existing database values.
   */
  @Converter(autoApply = true)
  public static class UserRoleConverter implements AttributeConverter<UserRole, String> {

    @Override
    public String convertToDatabaseColumn(final UserRole attribute) {
      if (attribute == null) {
        return null;
      }
      return attribute.getDatabaseValue();
    }

    @Override
    public UserRole convertToEntityAttribute(final String dbData) {
      return UserRole.fromDatabaseValue(dbData);
    }
  }
}
