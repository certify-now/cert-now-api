package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.CustomerProfile;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.CustomerProfileDTO;
import com.uk.certifynow.certify_now.repos.CustomerProfileRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.mappers.CustomerProfileMapper;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class CustomerProfileService {

  private final CustomerProfileRepository customerProfileRepository;
  private final UserRepository userRepository;
  private final CustomerProfileMapper customerProfileMapper;

  public CustomerProfileService(
      final CustomerProfileRepository customerProfileRepository,
      final UserRepository userRepository,
      final CustomerProfileMapper customerProfileMapper) {
    this.customerProfileRepository = customerProfileRepository;
    this.userRepository = userRepository;
    this.customerProfileMapper = customerProfileMapper;
  }

  public List<CustomerProfileDTO> findAll() {
    final List<CustomerProfile> customerProfiles = customerProfileRepository.findAll(Sort.by("id"));
    return customerProfiles.stream().map(customerProfileMapper::toDTO).toList();
  }

  public CustomerProfileDTO get(final UUID id) {
    return customerProfileRepository
        .findById(id)
        .map(customerProfileMapper::toDTO)
        .orElseThrow(NotFoundException::new);
  }

  public CustomerProfileDTO getByUserId(final UUID userId) {
    CustomerProfile profile = customerProfileRepository.findFirstByUserId(userId);
    if (profile == null) {
      throw new NotFoundException("Profile not found for user " + userId);
    }
    return customerProfileMapper.toDTO(profile);
  }

  @Transactional
  public void incrementPropertyCount(final UUID userId) {
    CustomerProfile profile = customerProfileRepository.findFirstByUserId(userId);
    if (profile != null) {
      profile.setTotalProperties(
          (profile.getTotalProperties() == null ? 0 : profile.getTotalProperties()) + 1);
      customerProfileRepository.save(profile);
      log.info("Incremented property count for user {}", userId);
    }
  }

  @Transactional
  public UUID create(final CustomerProfileDTO customerProfileDTO) {
    final CustomerProfile customerProfile = new CustomerProfile();
    customerProfileMapper.updateEntity(customerProfileDTO, customerProfile);
    // Resolve user reference from UUID
    final User user =
        customerProfileDTO.getUser() == null
            ? null
            : userRepository
                .findById(customerProfileDTO.getUser())
                .orElseThrow(() -> new NotFoundException("user not found"));
    customerProfile.setUser(user);
    UUID savedId = customerProfileRepository.save(customerProfile).getId();
    log.info("CustomerProfile {} created for user {}", savedId, customerProfileDTO.getUser());
    return savedId;
  }

  @Transactional
  public void update(final UUID id, final CustomerProfileDTO customerProfileDTO) {
    final CustomerProfile customerProfile =
        customerProfileRepository.findById(id).orElseThrow(NotFoundException::new);
    customerProfileMapper.updateEntity(customerProfileDTO, customerProfile);
    // Resolve user reference from UUID
    final User user =
        customerProfileDTO.getUser() == null
            ? null
            : userRepository
                .findById(customerProfileDTO.getUser())
                .orElseThrow(() -> new NotFoundException("user not found"));
    customerProfile.setUser(user);
    customerProfileRepository.save(customerProfile);
    log.info("CustomerProfile {} updated", id);
  }

  @Transactional
  public void delete(final UUID id) {
    final CustomerProfile customerProfile =
        customerProfileRepository.findById(id).orElseThrow(NotFoundException::new);
    customerProfileRepository.delete(customerProfile);
    log.info("CustomerProfile {} deleted", id);
  }

  @EventListener(BeforeDeleteUser.class)
  public void on(final BeforeDeleteUser event) {
    final ReferencedException referencedException = new ReferencedException();
    final CustomerProfile userCustomerProfile =
        customerProfileRepository.findFirstByUserId(event.getId());
    if (userCustomerProfile != null) {
      referencedException.setKey("user.customerProfile.user.referenced");
      referencedException.addParam(userCustomerProfile.getId());
      throw referencedException;
    }
  }
}
