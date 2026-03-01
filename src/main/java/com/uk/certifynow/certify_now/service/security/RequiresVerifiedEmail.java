package com.uk.certifynow.certify_now.service.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method or controller handler that requires the authenticated user to have a
 * verified email address.
 *
 * <p>Enforced by {@link RequiresVerifiedEmailAspect}. Throws a {@code BusinessException(403,
 * EMAIL_NOT_VERIFIED)} if the authenticated user's {@code emailVerified} flag is {@code false}.
 *
 * <p>Apply to any sensitive CUSTOMER actions — payments, job booking, profile updates, etc.
 *
 * <pre>{@code
 * &#64;RequiresVerifiedEmail
 * public void initiatePayment(...) { ... }
 * }</pre>
 *
 * <p><b>Note:</b> This is a DB lookup, not a JWT claim check. Claims can be stale (15-min window);
 * this annotation always reads the current verified state from the database.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresVerifiedEmail {}
