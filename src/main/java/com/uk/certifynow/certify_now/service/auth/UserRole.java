package com.uk.certifynow.certify_now.service.auth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Enum representing user roles in the CertifyNow platform. */
public enum UserRole {
  CUSTOMER,
  ENGINEER,
  ADMIN;

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
   * column.
   */
  @Converter(autoApply = true)
  public static class UserRoleConverter implements AttributeConverter<UserRole, String> {

    @Override
    public String convertToDatabaseColumn(final UserRole attribute) {
      return attribute == null ? null : attribute.name();
    }

    @Override
    public UserRole convertToEntityAttribute(final String dbData) {
      return dbData == null ? null : UserRole.valueOf(dbData);
    }
  }
}
