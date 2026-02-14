package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.UserDTO;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;


@Service
public class UserService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher publisher;

    public UserService(final UserRepository userRepository,
            final ApplicationEventPublisher publisher) {
        this.userRepository = userRepository;
        this.publisher = publisher;
    }

    public List<UserDTO> findAll() {
        final List<User> users = userRepository.findAll(Sort.by("id"));
        return users.stream()
                .map(user -> mapToDTO(user, new UserDTO()))
                .toList();
    }

    public UserDTO get(final UUID id) {
        return userRepository.findById(id)
                .map(user -> mapToDTO(user, new UserDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final UserDTO userDTO) {
        final User user = new User();
        mapToEntity(userDTO, user);
        return userRepository.save(user).getId();
    }

    public void update(final UUID id, final UserDTO userDTO) {
        final User user = userRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(userDTO, user);
        userRepository.save(user);
    }

    public void delete(final UUID id) {
        final User user = userRepository.findById(id)
                .orElseThrow(NotFoundException::new);
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
        userDTO.setAuthProvider(user.getAuthProvider());
        userDTO.setAvatarUrl(user.getAvatarUrl());
        userDTO.setEmail(user.getEmail());
        userDTO.setExternalAuthId(user.getExternalAuthId());
        userDTO.setFullName(user.getFullName());
        userDTO.setPasswordHash(user.getPasswordHash());
        userDTO.setRole(user.getRole());
        userDTO.setStatus(user.getStatus());
        return userDTO;
    }

    private User mapToEntity(final UserDTO userDTO, final User user) {
        user.setEmailVerified(userDTO.getEmailVerified());
        user.setPhoneVerified(userDTO.getPhoneVerified());
        user.setCreatedAt(userDTO.getCreatedAt());
        user.setLastLoginAt(userDTO.getLastLoginAt());
        user.setUpdatedAt(userDTO.getUpdatedAt());
        user.setPhone(userDTO.getPhone());
        user.setAuthProvider(userDTO.getAuthProvider());
        user.setAvatarUrl(userDTO.getAvatarUrl());
        user.setEmail(userDTO.getEmail());
        user.setExternalAuthId(userDTO.getExternalAuthId());
        user.setFullName(userDTO.getFullName());
        user.setPasswordHash(userDTO.getPasswordHash());
        user.setRole(userDTO.getRole());
        user.setStatus(userDTO.getStatus());
        return user;
    }

}
