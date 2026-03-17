package com.uk.certifynow.certify_now.service.auth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Set;

/**
 * Enum representing the application and verification status of an engineer. Encapsulates the
 * engineer onboarding workflow state machine.
 */
public enum EngineerApplicationStatus {
  APPLICATION_SUBMITTED,
  ID_VERIFICATION_PENDING,
  DBS_CHECK_PENDING,
  INSURANCE_VERIFICATION_PENDING,
  TRAINING_REQUIRED,
  APPROVED,
  REJECTED;

  /**
   * Returns the set of statuses that this status can transition to.
   *
   * @return set of valid next statuses
   */
  public Set<EngineerApplicationStatus> allowedTransitions() {
    return switch (this) {
      case APPLICATION_SUBMITTED -> Set.of(ID_VERIFICATION_PENDING, REJECTED);
      case ID_VERIFICATION_PENDING -> Set.of(DBS_CHECK_PENDING, REJECTED);
      case DBS_CHECK_PENDING -> Set.of(INSURANCE_VERIFICATION_PENDING, REJECTED);
      case INSURANCE_VERIFICATION_PENDING -> Set.of(TRAINING_REQUIRED, APPROVED, REJECTED);
      case TRAINING_REQUIRED -> Set.of(APPROVED, REJECTED);
      case APPROVED, REJECTED -> Set.of();
    };
  }

  /**
   * Checks if transitioning to the given target status is valid.
   *
   * @param target the target status
   * @return true if the transition is allowed, false otherwise
   */
  public boolean canTransitionTo(final EngineerApplicationStatus target) {
    return allowedTransitions().contains(target);
  }

  /**
   * Determines if the engineer application is approved.
   *
   * @return true if approved, false otherwise
   */
  public boolean isApproved() {
    return this == APPROVED;
  }

  /**
   * Determines if the engineer application is pending verification.
   *
   * @return true if pending (not approved or rejected), false otherwise
   */
  public boolean isPending() {
    return this != APPROVED && this != REJECTED;
  }

  /**
   * Determines if the engineer application is rejected.
   *
   * @return true if rejected, false otherwise
   */
  public boolean isRejected() {
    return this == REJECTED;
  }

  /**
   * JPA AttributeConverter for automatic conversion between EngineerApplicationStatus enum and
   * String database column.
   */
  @Converter(autoApply = true)
  public static class EngineerApplicationStatusConverter
      implements AttributeConverter<EngineerApplicationStatus, String> {

    @Override
    public String convertToDatabaseColumn(final EngineerApplicationStatus attribute) {
      return attribute == null ? null : attribute.name();
    }

    @Override
    public EngineerApplicationStatus convertToEntityAttribute(final String dbData) {
      return dbData == null ? null : EngineerApplicationStatus.valueOf(dbData);
    }
  }
}
