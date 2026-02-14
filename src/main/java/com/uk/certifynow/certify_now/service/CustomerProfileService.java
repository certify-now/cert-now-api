package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.CustomerProfile;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.CustomerProfileDTO;
import com.uk.certifynow.certify_now.repos.CustomerProfileRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;


@Service
public class CustomerProfileService {

    private final CustomerProfileRepository customerProfileRepository;
    private final UserRepository userRepository;

    public CustomerProfileService(final CustomerProfileRepository customerProfileRepository,
            final UserRepository userRepository) {
        this.customerProfileRepository = customerProfileRepository;
        this.userRepository = userRepository;
    }

    public List<CustomerProfileDTO> findAll() {
        final List<CustomerProfile> customerProfiles = customerProfileRepository.findAll(Sort.by("id"));
        return customerProfiles.stream()
                .map(customerProfile -> mapToDTO(customerProfile, new CustomerProfileDTO()))
                .toList();
    }

    public CustomerProfileDTO get(final UUID id) {
        return customerProfileRepository.findById(id)
                .map(customerProfile -> mapToDTO(customerProfile, new CustomerProfileDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final CustomerProfileDTO customerProfileDTO) {
        final CustomerProfile customerProfile = new CustomerProfile();
        mapToEntity(customerProfileDTO, customerProfile);
        return customerProfileRepository.save(customerProfile).getId();
    }

    public void update(final UUID id, final CustomerProfileDTO customerProfileDTO) {
        final CustomerProfile customerProfile = customerProfileRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(customerProfileDTO, customerProfile);
        customerProfileRepository.save(customerProfile);
    }

    public void delete(final UUID id) {
        final CustomerProfile customerProfile = customerProfileRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        customerProfileRepository.delete(customerProfile);
    }

    private CustomerProfileDTO mapToDTO(final CustomerProfile customerProfile,
            final CustomerProfileDTO customerProfileDTO) {
        customerProfileDTO.setId(customerProfile.getId());
        customerProfileDTO.setComplianceScore(customerProfile.getComplianceScore());
        customerProfileDTO.setIsLettingAgent(customerProfile.getIsLettingAgent());
        customerProfileDTO.setTotalProperties(customerProfile.getTotalProperties());
        customerProfileDTO.setCreatedAt(customerProfile.getCreatedAt());
        customerProfileDTO.setUpdatedAt(customerProfile.getUpdatedAt());
        customerProfileDTO.setCompanyName(customerProfile.getCompanyName());
        customerProfileDTO.setNotificationPrefs(customerProfile.getNotificationPrefs());
        customerProfileDTO.setUser(customerProfile.getUser() == null ? null : customerProfile.getUser().getId());
        return customerProfileDTO;
    }

    private CustomerProfile mapToEntity(final CustomerProfileDTO customerProfileDTO,
            final CustomerProfile customerProfile) {
        customerProfile.setComplianceScore(customerProfileDTO.getComplianceScore());
        customerProfile.setIsLettingAgent(customerProfileDTO.getIsLettingAgent());
        customerProfile.setTotalProperties(customerProfileDTO.getTotalProperties());
        customerProfile.setCreatedAt(customerProfileDTO.getCreatedAt());
        customerProfile.setUpdatedAt(customerProfileDTO.getUpdatedAt());
        customerProfile.setCompanyName(customerProfileDTO.getCompanyName());
        customerProfile.setNotificationPrefs(customerProfileDTO.getNotificationPrefs());
        final User user = customerProfileDTO.getUser() == null ? null : userRepository.findById(customerProfileDTO.getUser())
                .orElseThrow(() -> new NotFoundException("user not found"));
        customerProfile.setUser(user);
        return customerProfile;
    }

    @EventListener(BeforeDeleteUser.class)
    public void on(final BeforeDeleteUser event) {
        final ReferencedException referencedException = new ReferencedException();
        final CustomerProfile userCustomerProfile = customerProfileRepository.findFirstByUserId(event.getId());
        if (userCustomerProfile != null) {
            referencedException.setKey("user.customerProfile.user.referenced");
            referencedException.addParam(userCustomerProfile.getId());
            throw referencedException;
        }
    }

}
