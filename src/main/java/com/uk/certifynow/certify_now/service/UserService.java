package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.model.UpdateMeRequest;
import com.uk.certifynow.certify_now.model.UserDTO;
import com.uk.certifynow.certify_now.model.UserMeDTO;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.auth.AuthProvider;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import com.uk.certifynow.certify_now.service.auth.UserStatus;
import com.uk.certifynow.certify_now.util.NotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserService {

  private final UserRepository userRepository;
  private final ApplicationEventPublisher publisher;

  public UserService(
      final UserRepository userRepository, final ApplicationEventPublisher publisher) {
    this.userRepository = userRepository;
    this.publisher = publisher;
  }

  @Cacheable("users_all")
  public List<UserDTO> findAll() {
    final List<User> users = userRepository.findAll(Sort.by("id"));
    return users.stream().map(user -> mapToDTO(user, new UserDTO())).toList();
  }

  @Cacheable(value = "users", key = "#id")
  public UserDTO get(final UUID id) {
    return userRepository
        .findById(id)
        .map(user -> mapToDTO(user, new UserDTO()))
        .orElseThrow(NotFoundException::new);
  }

  @Cacheable(value = "users_email", key = "#email")
  public UserDTO getByEmail(final String email) {
    return userRepository
        .findByEmailIgnoreCase(email)
        .map(user -> mapToDTO(user, new UserDTO()))
        .orElseThrow(NotFoundException::new);
  }

  @Cacheable(value = "users_me", key = "#id")
  public UserMeDTO getMe(final UUID id) {
    return userRepository
        .findById(id)
        .map(user -> mapToMeDTO(user, new UserMeDTO()))
        .orElseThrow(NotFoundException::new);
  }

  @CacheEvict(
      value = {"users", "users_all", "users_email", "users_me"},
      allEntries = true)
  public UUID create(final UserDTO userDTO) {
    final User user = new User();
    mapToEntity(userDTO, user);
    return userRepository.save(user).getId();
  }

  @CacheEvict(
      value = {"users", "users_all", "users_email", "users_me"},
      allEntries = true)
  public void update(final UUID id, final UserDTO userDTO) {
    final User user = userRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(userDTO, user);
    userRepository.save(user);
  }

  @CacheEvict(
      value = {"users", "users_all", "users_email", "users_me"},
      allEntries = true)
  public void updateMe(final UUID id, final UpdateMeRequest updateMeRequest) {
    final User user = userRepository.findById(id).orElseThrow(NotFoundException::new);

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

    user.setUpdatedAt(OffsetDateTime.now());
    userRepository.save(user);
  }

  @CacheEvict(
      value = {"users", "users_all", "users_email", "users_me"},
      allEntries = true)
  public void delete(final UUID id) {
    final User user = userRepository.findById(id).orElseThrow(NotFoundException::new);
    publisher.publishEvent(new BeforeDeleteUser(id));
    userRepository.delete(user);
  }

  private UserDTO mapToDTO(final User user, final UserDTO userDTO) {
    userDTO.setId(user.getId());
    userDTO.setEmailVerified(user.getEmailVerified());
    userDTO.setPhoneVerified(user.getPhoneVerified());
    userDTO.setCreatedAt(user.getCreatedAt());
    userDTO.setLastLoginAt(user.getLastLoginAt());
    userDTO.setUpdatedAt(user.getUpdatedAt());
    userDTO.setPhone(user.getPhone());
    userDTO.setAuthProvider(user.getAuthProvider().name());
    userDTO.setAvatarUrl(user.getAvatarUrl());
    userDTO.setEmail(user.getEmail());
    userDTO.setExternalAuthId(user.getExternalAuthId());
    userDTO.setFullName(user.getFullName());
    userDTO.setPasswordHash(user.getPasswordHash());
    userDTO.setRole(user.getRole().name());
    userDTO.setStatus(user.getStatus().name());
    return userDTO;
  }

  private UserMeDTO mapToMeDTO(final User user, final UserMeDTO userMeDTO) {
    userMeDTO.setId(user.getId());
    userMeDTO.setEmail(user.getEmail());
    userMeDTO.setFullName(user.getFullName());
    userMeDTO.setPhone(user.getPhone());
    userMeDTO.setAvatarUrl(user.getAvatarUrl());
    userMeDTO.setRole(user.getRole().name());
    userMeDTO.setStatus(user.getStatus().name());
    userMeDTO.setEmailVerified(user.getEmailVerified());
    userMeDTO.setPhoneVerified(user.getPhoneVerified());
    userMeDTO.setCreatedAt(user.getCreatedAt());
    userMeDTO.setUpdatedAt(user.getUpdatedAt());
    userMeDTO.setLastLoginAt(user.getLastLoginAt());
    return userMeDTO;
  }

  private User mapToEntity(final UserDTO userDTO, final User user) {
    user.setEmailVerified(userDTO.getEmailVerified());
    user.setPhoneVerified(userDTO.getPhoneVerified());
    user.setCreatedAt(userDTO.getCreatedAt());
    user.setLastLoginAt(userDTO.getLastLoginAt());
    user.setUpdatedAt(userDTO.getUpdatedAt());
    user.setPhone(userDTO.getPhone());
    user.setAuthProvider(AuthProvider.valueOf(userDTO.getAuthProvider()));
    user.setAvatarUrl(userDTO.getAvatarUrl());
    user.setEmail(userDTO.getEmail());
    user.setExternalAuthId(userDTO.getExternalAuthId());
    user.setFullName(userDTO.getFullName());
    user.setPasswordHash(userDTO.getPasswordHash());
    user.setRole(UserRole.valueOf(userDTO.getRole()));
    user.setStatus(UserStatus.valueOf(userDTO.getStatus()));
    return user;
  }
}
