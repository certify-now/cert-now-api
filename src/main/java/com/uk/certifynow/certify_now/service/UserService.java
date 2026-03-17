package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.AccountDeactivatedEvent;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.events.UserRestoredEvent;
import com.uk.certifynow.certify_now.events.UserSoftDeletedEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.model.UpdateMeRequest;
import com.uk.certifynow.certify_now.model.UserDTO;
import com.uk.certifynow.certify_now.model.UserMeDTO;
import com.uk.certifynow.certify_now.repos.CustomerProfileRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.RefreshTokenRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import com.uk.certifynow.certify_now.service.auth.UserStatus;
import com.uk.certifynow.certify_now.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.service.job.JobStatus;
import com.uk.certifynow.certify_now.service.mappers.UserMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UserService {


  private final UserRepository userRepository;
  private final JobRepository jobRepository;
  private final CustomerProfileRepository customerProfileRepository;
  private final EngineerProfileRepository engineerProfileRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final ApplicationEventPublisher publisher;
  private final UserMapper userMapper;
  private final Clock clock;

  public UserService(
      final UserRepository userRepository,
      final JobRepository jobRepository,
      final CustomerProfileRepository customerProfileRepository,
      final EngineerProfileRepository engineerProfileRepository,
      final RefreshTokenRepository refreshTokenRepository,
      final ApplicationEventPublisher publisher,
      final UserMapper userMapper,
      final Clock clock) {
    this.userRepository = userRepository;
    this.jobRepository = jobRepository;
    this.customerProfileRepository = customerProfileRepository;
    this.engineerProfileRepository = engineerProfileRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.publisher = publisher;
    this.userMapper = userMapper;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<UserDTO> findAll() {
    final List<User> users = userRepository.findAll(Sort.by("id"));
    return users.stream().map(userMapper::toDTO).toList();
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "users", key = "#id")
  public UserDTO get(final UUID id) {
    return userRepository
        .findById(id)
        .map(userMapper::toDTO)
        .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "users_email", key = "#email")
  public UserDTO getByEmail(final String email) {
    return userRepository
        .findByEmailIgnoreCase(email)
        .map(userMapper::toDTO)
        .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + email));
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "users_me", key = "#id")
  public UserMeDTO getMe(final UUID id) {
    return userRepository
        .findById(id)
        .map(userMapper::toMeDTO)
        .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
  }

  @Transactional
  @CacheEvict(
      value = {"users", "users_all", "users_email", "users_me"},
      allEntries = true)
  public UUID create(final UserDTO userDTO) {
    final User user = new User();
    userMapper.updateEntity(userDTO, user);
    UUID savedId = userRepository.save(user).getId();
    log.info("User {} created with role {}", savedId, userDTO.getRole());
    return savedId;
  }

  @Transactional
  @CacheEvict(
      value = {"users", "users_all", "users_email", "users_me"},
      allEntries = true)
  public void update(final UUID id, final UserDTO userDTO) {
    final User user = userRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    userMapper.updateEntity(userDTO, user);
    userRepository.save(user);
    log.info("User {} updated", id);
  }

  @Transactional
  @CacheEvict(
      value = {"users", "users_all", "users_email", "users_me"},
      allEntries = true)
  public void updateMe(final UUID id, final UpdateMeRequest updateMeRequest) {
    final User user = userRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("User not found: " + id));

    if (updateMeRequest.getFullName() != null) {
      user.setFullName(updateMeRequest.getFullName().trim());
    }

    if (updateMeRequest.getPhone() != null) {
      final String normalizedPhone = updateMeRequest.getPhone().trim();
      if (normalizedPhone.isBlank()) {
        user.setPhone(null);
      } else {
        userRepository
            .findByPhone(normalizedPhone)
            .filter(existingUser -> !existingUser.getId().equals(id))
            .ifPresent(
                existingUser -> {
                  throw new BusinessException(
                      HttpStatus.CONFLICT,
                      "PHONE_ALREADY_IN_USE",
                      "Phone number is already in use");
                });
        user.setPhone(normalizedPhone);
      }
    }

    if (updateMeRequest.getAvatarUrl() != null) {
      user.setAvatarUrl(updateMeRequest.getAvatarUrl().trim());
    }

    user.setUpdatedAt(OffsetDateTime.now(clock));
    userRepository.save(user);
    log.info("User {} updated their profile", id);
  }

  @Transactional
  @CacheEvict(
      value = {"users", "users_all", "users_email", "users_me"},
      allEntries = true)
  public void delete(final UUID id) {
    final User user = userRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    publisher.publishEvent(new BeforeDeleteUser(id));
    userRepository.delete(user);
    log.info("User {} deleted", id);
  }

  // ── Soft-delete operations ──────────────────────────────────────────────────

  /**
   * Soft-deletes a user by setting deletedAt/deletedBy. Validates there are no active
   * (non-terminal) jobs. Cascades soft-delete to associated profile, invalidates refresh tokens,
   * and publishes a domain event.
   */
  @Transactional
  @CacheEvict(
      value = {"users", "users_all", "users_email", "users_me"},
      allEntries = true)
  public void softDelete(final UUID userId, final UUID deletedByUserId) {
    final User user =
        userRepository.findByIdIncludingDeleted(userId).orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

    if (user.isDeleted()) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "ALREADY_DELETED", "User is already soft-deleted");
    }

    // Validate: no active (non-terminal) jobs
    final boolean hasActiveCustomerJobs =
        jobRepository.existsActiveJobsByCustomerId(userId, JobStatus.TERMINAL_STATUSES);
    final boolean hasActiveEngineerJobs =
        jobRepository.existsActiveJobsByEngineerId(userId, JobStatus.TERMINAL_STATUSES);
    if (hasActiveCustomerJobs || hasActiveEngineerJobs) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "ACTIVE_JOBS_EXIST", "Cannot soft-delete user with active jobs");
    }

    final OffsetDateTime now = OffsetDateTime.now(clock);
    user.setDeletedAt(now);
    user.setDeletedBy(deletedByUserId);
    user.setStatus(UserStatus.DEACTIVATED);
    user.setUpdatedAt(now);
    userRepository.save(user);

    // Cascade soft-delete to profile
    cascadeSoftDeleteProfile(user, now, deletedByUserId);

    // Invalidate all refresh tokens
    refreshTokenRepository.deleteAllByUserId(userId);

    log.info("User {} soft-deleted by {}", userId, deletedByUserId);
    publisher.publishEvent(new UserSoftDeletedEvent(userId, deletedByUserId));

    final String initiatedBy = userId.equals(deletedByUserId) ? "USER" : "ADMIN";
    final Long accountAgeInDays =
        user.getCreatedAt() != null ? ChronoUnit.DAYS.between(user.getCreatedAt(), now) : null;
    publisher.publishEvent(
        new AccountDeactivatedEvent(
            userId, user.getEmail(), "ACCOUNT_DEACTIVATED", initiatedBy, accountAgeInDays, null));
  }

  /**
   * Restores a soft-deleted user by clearing deletedAt/deletedBy and setting status to ACTIVE.
   * Cascades restore to associated profile.
   */
  @Transactional
  @CacheEvict(
      value = {"users", "users_all", "users_email", "users_me"},
      allEntries = true)
  public void restore(final UUID userId, final UUID restoredByUserId) {
    final User user =
        userRepository.findByIdIncludingDeleted(userId).orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

    if (!user.isDeleted()) {
      throw new BusinessException(HttpStatus.CONFLICT, "NOT_DELETED", "User is not soft-deleted");
    }

    final OffsetDateTime now = OffsetDateTime.now(clock);
    user.setDeletedAt(null);
    user.setDeletedBy(null);
    user.setStatus(UserStatus.ACTIVE);
    user.setUpdatedAt(now);
    userRepository.save(user);

    // Cascade restore to profile
    cascadeRestoreProfile(user, now);

    log.info("User {} restored by {}", userId, restoredByUserId);
    publisher.publishEvent(new UserRestoredEvent(userId, restoredByUserId));
  }

  private void cascadeSoftDeleteProfile(
      final User user, final OffsetDateTime now, final UUID deletedBy) {
    if (user.getRole() == UserRole.CUSTOMER) {
      customerProfileRepository
          .findByUserIdIncludingDeleted(user.getId())
          .ifPresent(
              profile -> {
                profile.setDeletedAt(now);
                profile.setDeletedBy(deletedBy);
                customerProfileRepository.save(profile);
              });
    } else if (user.getRole() == UserRole.ENGINEER) {
      engineerProfileRepository
          .findByUserIdIncludingDeleted(user.getId())
          .ifPresent(
              profile -> {
                profile.setDeletedAt(now);
                profile.setDeletedBy(deletedBy);
                profile.setIsOnline(false);
                engineerProfileRepository.save(profile);
              });
    }
  }

  private void cascadeRestoreProfile(final User user, final OffsetDateTime now) {
    if (user.getRole() == UserRole.CUSTOMER) {
      customerProfileRepository
          .findByUserIdIncludingDeleted(user.getId())
          .ifPresent(
              profile -> {
                profile.setDeletedAt(null);
                profile.setDeletedBy(null);
                customerProfileRepository.save(profile);
              });
    } else if (user.getRole() == UserRole.ENGINEER) {
      engineerProfileRepository
          .findByUserIdIncludingDeleted(user.getId())
          .ifPresent(
              profile -> {
                profile.setDeletedAt(null);
                profile.setDeletedBy(null);
                engineerProfileRepository.save(profile);
              });
    }
  }
}
