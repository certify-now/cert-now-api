package com.uk.certifynow.certify_now.service.auth.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ensures that the role supplied in a self-registration request is one that users are permitted to
 * choose for themselves. ADMIN cannot be self-registered.
 */
@Documented
@Constraint(validatedBy = SelfRegistrableRoleValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSelfRegistrableRole {

  String message() default "role must be CUSTOMER or ENGINEER — ADMIN cannot be self-registered";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
