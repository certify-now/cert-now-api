package com.uk.certifynow.certify_now.service.auth.dto;

import com.uk.certifynow.certify_now.service.auth.UserRole;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Rejects UserRole.ADMIN on self-registration. null is allowed here — @NotNull on the field handles
 * the null case separately so error messages stay distinct.
 */
public class SelfRegistrableRoleValidator
    implements ConstraintValidator<ValidSelfRegistrableRole, UserRole> {

  @Override
  public boolean isValid(final UserRole role, final ConstraintValidatorContext context) {
    if (role == null) {
      return true; // @NotNull handles this separately
    }
    return role == UserRole.CUSTOMER || role == UserRole.ENGINEER;
  }
}
