package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.model.UpdateMeRequest;
import com.uk.certifynow.certify_now.model.UserDTO;
import com.uk.certifynow.certify_now.model.UserMeDTO;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.mappers.UserMapper;
import com.uk.certifynow.certify_now.util.NotFoundException;
import java.time.OffsetDateTime;
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
  private final ApplicationEventPublisher publisher;
  private final UserMapper userMapper;

  public UserService(
      final UserRepository userRepository,
      final ApplicationEventPublisher publisher,
      final UserMapper userMapper) {
    this.userRepository = userRepository;
    this.publisher = publisher;
    this.userMapper = userMapper;
  }

  @Cacheable("users_all")
  public List<UserDTO> findAll() {
    final List<User> users = userRepository.findAll(Sort.by("id"));
    return users.stream().map(userMapper::toDTO).toList();
  }

  @Cacheable(value = "users", key = "#id")
  public UserDTO get(final UUID id) {
    return userRepository.findById(id).map(userMapper::toDTO).orElseThrow(NotFoundException::new);
  }

  @Cacheable(value = "users_email", key = "#email")
  public UserDTO getByEmail(final String email) {
    return userRepository
        .findByEmailIgnoreCase(email)
        .map(userMapper::toDTO)
        .orElseThrow(NotFoundException::new);
  }

  @Cacheable(value = "users_me", key = "#id")
  public UserMeDTO getMe(final UUID id) {
    return userRepository.findById(id).map(userMapper::toMeDTO).orElseThrow(NotFoundException::new);
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
    final User user = userRepository.findById(id).orElseThrow(NotFoundException::new);
    userMapper.updateEntity(userDTO, user);
    userRepository.save(user);
    log.info("User {} updated", id);
  }

  @Transactional
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
    log.info("User {} updated their profile", id);
  }

  @Transactional
  @CacheEvict(
      value = {"users", "users_all", "users_email", "users_me"},
      allEntries = true)
  public void delete(final UUID id) {
    final User user = userRepository.findById(id).orElseThrow(NotFoundException::new);
    publisher.publishEvent(new BeforeDeleteUser(id));
    userRepository.delete(user);
    log.info("User {} deleted", id);
  }
}
