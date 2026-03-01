package com.uk.certifynow.certify_now.service.security;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.repos.UserRepository;
import java.util.UUID;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * AOP aspect enforcing {@link RequiresVerifiedEmail}.
 *
 * <p>Fix 8: Checks the annotated method's caller has a verified email by reading the current value
 * from the database — this is intentionally NOT delegated to the JWT status claim, which can be up
 * to 15 minutes stale. Privileged actions must use the live DB value.
 *
 * <p>Requires the {@code spring-boot-starter-aop} dependency (included transitively via {@code
 * spring-boot-starter-web}).
 */
@Aspect
@Component
public class RequiresVerifiedEmailAspect {

  private final UserRepository userRepository;

  public RequiresVerifiedEmailAspect(final UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  /**
   * Intercepts any method annotated with {@link RequiresVerifiedEmail} and verifies the
   * authenticated user's email is confirmed.
   *
   * @throws BusinessException 403 EMAIL_NOT_VERIFIED if the user's email is unverified
   * @throws BusinessException 401 UNAUTHORIZED if there is no authenticated principal
   */
  @Before("@annotation(com.uk.certifynow.certify_now.shared.security.RequiresVerifiedEmail)")
  public void checkEmailVerified() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }

    final String principalStr = (String) authentication.getPrincipal();
    final UUID userId;
    try {
      userId = UUID.fromString(principalStr);
    } catch (final IllegalArgumentException ex) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid authentication principal");
    }

    final User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authenticated user not found"));

    if (!Boolean.TRUE.equals(user.getEmailVerified())) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "EMAIL_NOT_VERIFIED",
          "Email verification is required to perform this action. "
              + "Please check your inbox for the verification email.");
    }
  }
}
