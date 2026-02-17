package com.uk.certifynow.certify_now.auth.mapping;

import com.uk.certifynow.certify_now.auth.api.response.CustomerProfileView;
import com.uk.certifynow.certify_now.auth.api.response.EngineerProfileView;
import com.uk.certifynow.certify_now.auth.api.response.ProfileView;
import com.uk.certifynow.certify_now.auth.application.SessionService;
import com.uk.certifynow.certify_now.auth.domain.UserRole;
import com.uk.certifynow.certify_now.auth.dto.AuthResponse;
import com.uk.certifynow.certify_now.domain.CustomerProfile;
import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.CustomerProfileRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.shared.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.shared.security.JwtTokenProvider;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Centralized DTO mapping for authentication responses.
 *
 * <p>Responsibilities: - Map domain entities to DTOs - Construct AuthResponse with user summary and
 * profile views - Handle polymorphic profile views using sealed interface
 *
 * <p>This mapper ensures that services don't construct DTOs directly, maintaining clean separation
 * between domain and API layers.
 */
@Component
public class AuthMapper {

  private final UserRepository userRepository;
  private final CustomerProfileRepository customerProfileRepository;
  private final EngineerProfileRepository engineerProfileRepository;
  private final JwtTokenProvider jwtTokenProvider;

  public AuthMapper(
      final UserRepository userRepository,
      final CustomerProfileRepository customerProfileRepository,
      final EngineerProfileRepository engineerProfileRepository,
      final JwtTokenProvider jwtTokenProvider) {
    this.userRepository = userRepository;
    this.customerProfileRepository = customerProfileRepository;
    this.engineerProfileRepository = engineerProfileRepository;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  /**
   * Maps a User and token pair to an AuthResponse.
   *
   * @param user authenticated user
   * @param tokens access and refresh tokens
   * @return complete authentication response
   */
  public AuthResponse toAuthResponse(final User user, final SessionService.TokenPair tokens) {
    final ProfileView profile = loadProfile(user.getId(), user.getRole());
    final AuthResponse.UserSummary userSummary = toUserSummary(user, profile);

    return new AuthResponse(
        tokens.accessToken(),
        tokens.refreshToken(),
        "Bearer",
        jwtTokenProvider.getAccessTokenExpirySeconds(),
        userSummary);
  }

  /**
   * Maps tokens to an AuthResponse by extracting user from JWT.
   *
   * <p>Used for refresh token flow where we don't have the User entity readily available.
   *
   * @param tokens access and refresh tokens
   * @return complete authentication response
   */
  public AuthResponse toAuthResponseFromToken(final SessionService.TokenPair tokens) {
    final UUID userId = jwtTokenProvider.getUserIdFromToken(tokens.accessToken());
    final User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    return toAuthResponse(user, tokens);
  }

  /**
   * Maps a user ID to a UserSummary with profile.
   *
   * <p>Used for the /me endpoint.
   *
   * @param userId user ID
   * @return user summary with profile
   */
  public AuthResponse.UserSummary toUserSummary(final UUID userId) {
    final User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    final ProfileView profile = loadProfile(user.getId(), user.getRole());
    return toUserSummary(user, profile);
  }

  /**
   * Loads the appropriate profile based on user role.
   *
   * @param userId user ID
   * @param role user role
   * @return polymorphic profile view (CustomerProfileView or EngineerProfileView)
   */
  private ProfileView loadProfile(final UUID userId, final UserRole role) {
    return switch (role) {
      case CUSTOMER -> loadCustomerProfile(userId);
      case ENGINEER -> loadEngineerProfile(userId);
      case ADMIN -> null; // Admins don't have profiles
    };
  }

  /**
   * Loads customer profile and maps to view.
   *
   * @param userId user ID
   * @return customer profile view or null if not found
   */
  private ProfileView loadCustomerProfile(final UUID userId) {
    final CustomerProfile profile = customerProfileRepository.findFirstByUserId(userId);
    if (profile == null) {
      return null;
    }

    return new CustomerProfileView(
        profile.getTotalProperties(),
        profile.getComplianceScore(),
        profile.getIsLettingAgent(),
        profile.getCompanyName());
  }

  /**
   * Loads engineer profile and maps to view.
   *
   * @param userId user ID
   * @return engineer profile view or null if not found
   */
  private ProfileView loadEngineerProfile(final UUID userId) {
    final EngineerProfile profile = engineerProfileRepository.findFirstByUserId(userId);
    if (profile == null) {
      return null;
    }

    return new EngineerProfileView(
        profile.getStatus().name(), // Convert enum to string for API
        profile.getTier().name(), // Convert enum to string for API
        profile.getBio(),
        profile.getServiceRadiusMiles(),
        profile.getAvgRating(),
        profile.getTotalReviews(),
        profile.getTotalJobsCompleted(),
        profile.getIsOnline(),
        profile.getStripeOnboarded());
  }

  /**
   * Maps User entity to UserSummary DTO.
   *
   * @param user user entity
   * @param profile polymorphic profile view
   * @return user summary DTO
   */
  private AuthResponse.UserSummary toUserSummary(final User user, final ProfileView profile) {
    return new AuthResponse.UserSummary(
        user.getId(),
        user.getEmail(),
        user.getFullName(),
        user.getPhone(),
        user.getRole().name(), // Convert enum to string for API
        user.getStatus().name(), // Convert enum to string for API
        user.getEmailVerified(),
        user.getPhoneVerified(),
        user.getAvatarUrl(),
        user.getCreatedAt(),
        user.getLastLoginAt(),
        profile);
  }
}
