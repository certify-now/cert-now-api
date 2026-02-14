package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.domain.UserConsent;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.UserConsentDTO;
import com.uk.certifynow.certify_now.repos.UserConsentRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;


@Service
public class UserConsentService {

    private final UserConsentRepository userConsentRepository;
    private final UserRepository userRepository;

    public UserConsentService(final UserConsentRepository userConsentRepository,
            final UserRepository userRepository) {
        this.userConsentRepository = userConsentRepository;
        this.userRepository = userRepository;
    }

    public List<UserConsentDTO> findAll() {
        final List<UserConsent> userConsents = userConsentRepository.findAll(Sort.by("id"));
        return userConsents.stream()
                .map(userConsent -> mapToDTO(userConsent, new UserConsentDTO()))
                .toList();
    }

    public UserConsentDTO get(final UUID id) {
        return userConsentRepository.findById(id)
                .map(userConsent -> mapToDTO(userConsent, new UserConsentDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final UserConsentDTO userConsentDTO) {
        final UserConsent userConsent = new UserConsent();
        mapToEntity(userConsentDTO, userConsent);
        return userConsentRepository.save(userConsent).getId();
    }

    public void update(final UUID id, final UserConsentDTO userConsentDTO) {
        final UserConsent userConsent = userConsentRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(userConsentDTO, userConsent);
        userConsentRepository.save(userConsent);
    }

    public void delete(final UUID id) {
        final UserConsent userConsent = userConsentRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        userConsentRepository.delete(userConsent);
    }

    private UserConsentDTO mapToDTO(final UserConsent userConsent,
            final UserConsentDTO userConsentDTO) {
        userConsentDTO.setId(userConsent.getId());
        userConsentDTO.setGranted(userConsent.getGranted());
        userConsentDTO.setCreatedAt(userConsent.getCreatedAt());
        userConsentDTO.setGrantedAt(userConsent.getGrantedAt());
        userConsentDTO.setRevokedAt(userConsent.getRevokedAt());
        userConsentDTO.setConsentType(userConsent.getConsentType());
        userConsentDTO.setIpAddress(userConsent.getIpAddress());
        userConsentDTO.setUserAgent(userConsent.getUserAgent());
        userConsentDTO.setUser(userConsent.getUser() == null ? null : userConsent.getUser().getId());
        return userConsentDTO;
    }

    private UserConsent mapToEntity(final UserConsentDTO userConsentDTO,
            final UserConsent userConsent) {
        userConsent.setGranted(userConsentDTO.getGranted());
        userConsent.setCreatedAt(userConsentDTO.getCreatedAt());
        userConsent.setGrantedAt(userConsentDTO.getGrantedAt());
        userConsent.setRevokedAt(userConsentDTO.getRevokedAt());
        userConsent.setConsentType(userConsentDTO.getConsentType());
        userConsent.setIpAddress(userConsentDTO.getIpAddress());
        userConsent.setUserAgent(userConsentDTO.getUserAgent());
        final User user = userConsentDTO.getUser() == null ? null : userRepository.findById(userConsentDTO.getUser())
                .orElseThrow(() -> new NotFoundException("user not found"));
        userConsent.setUser(user);
        return userConsent;
    }

    @EventListener(BeforeDeleteUser.class)
    public void on(final BeforeDeleteUser event) {
        final ReferencedException referencedException = new ReferencedException();
        final UserConsent userUserConsent = userConsentRepository.findFirstByUserId(event.getId());
        if (userUserConsent != null) {
            referencedException.setKey("user.userConsent.user.referenced");
            referencedException.addParam(userUserConsent.getId());
            throw referencedException;
        }
    }

}
