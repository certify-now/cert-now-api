package com.uk.certifynow.certify_now.service.notification;

import com.uk.certifynow.certify_now.domain.Certificate;
import com.uk.certifynow.certify_now.domain.Notification;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.RenewalReminder;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteCertificate;
import com.uk.certifynow.certify_now.events.BeforeDeleteNotification;
import com.uk.certifynow.certify_now.events.BeforeDeleteProperty;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.RenewalReminderDTO;
import com.uk.certifynow.certify_now.repos.CertificateRepository;
import com.uk.certifynow.certify_now.repos.NotificationRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.RenewalReminderRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class RenewalReminderService {

  private final RenewalReminderRepository renewalReminderRepository;
  private final CertificateRepository certificateRepository;
  private final UserRepository userRepository;
  private final NotificationRepository notificationRepository;
  private final PropertyRepository propertyRepository;

  public RenewalReminderService(
      final RenewalReminderRepository renewalReminderRepository,
      final CertificateRepository certificateRepository,
      final UserRepository userRepository,
      final NotificationRepository notificationRepository,
      final PropertyRepository propertyRepository) {
    this.renewalReminderRepository = renewalReminderRepository;
    this.certificateRepository = certificateRepository;
    this.userRepository = userRepository;
    this.notificationRepository = notificationRepository;
    this.propertyRepository = propertyRepository;
  }

  public List<RenewalReminderDTO> findAll() {
    final List<RenewalReminder> renewalReminders = renewalReminderRepository.findAll(Sort.by("id"));
    return renewalReminders.stream()
        .map(renewalReminder -> mapToDTO(renewalReminder, new RenewalReminderDTO()))
        .toList();
  }

  public RenewalReminderDTO get(final UUID id) {
    return renewalReminderRepository
        .findById(id)
        .map(renewalReminder -> mapToDTO(renewalReminder, new RenewalReminderDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public UUID create(final RenewalReminderDTO renewalReminderDTO) {
    final RenewalReminder renewalReminder = new RenewalReminder();
    mapToEntity(renewalReminderDTO, renewalReminder);
    return renewalReminderRepository.save(renewalReminder).getId();
  }

  public void update(final UUID id, final RenewalReminderDTO renewalReminderDTO) {
    final RenewalReminder renewalReminder =
        renewalReminderRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(renewalReminderDTO, renewalReminder);
    renewalReminderRepository.save(renewalReminder);
  }

  public void delete(final UUID id) {
    final RenewalReminder renewalReminder =
        renewalReminderRepository.findById(id).orElseThrow(NotFoundException::new);
    renewalReminderRepository.delete(renewalReminder);
  }

  private RenewalReminderDTO mapToDTO(
      final RenewalReminder renewalReminder, final RenewalReminderDTO renewalReminderDTO) {
    renewalReminderDTO.setId(renewalReminder.getId());
    renewalReminderDTO.setDaysBefore(renewalReminder.getDaysBefore());
    renewalReminderDTO.setExpiryDate(renewalReminder.getExpiryDate());
    renewalReminderDTO.setScheduledFor(renewalReminder.getScheduledFor());
    renewalReminderDTO.setSent(renewalReminder.getSent());
    renewalReminderDTO.setCreatedAt(renewalReminder.getCreatedAt());
    renewalReminderDTO.setSentAt(renewalReminder.getSentAt());
    renewalReminderDTO.setCertificateType(renewalReminder.getCertificateType());
    renewalReminderDTO.setCertificate(
        renewalReminder.getCertificate() == null ? null : renewalReminder.getCertificate().getId());
    renewalReminderDTO.setCustomer(
        renewalReminder.getCustomer() == null ? null : renewalReminder.getCustomer().getId());
    renewalReminderDTO.setNotification(
        renewalReminder.getNotification() == null
            ? null
            : renewalReminder.getNotification().getId());
    renewalReminderDTO.setProperty(
        renewalReminder.getProperty() == null ? null : renewalReminder.getProperty().getId());
    return renewalReminderDTO;
  }

  private RenewalReminder mapToEntity(
      final RenewalReminderDTO renewalReminderDTO, final RenewalReminder renewalReminder) {
    renewalReminder.setDaysBefore(renewalReminderDTO.getDaysBefore());
    renewalReminder.setExpiryDate(renewalReminderDTO.getExpiryDate());
    renewalReminder.setScheduledFor(renewalReminderDTO.getScheduledFor());
    renewalReminder.setSent(renewalReminderDTO.getSent());
    renewalReminder.setCreatedAt(renewalReminderDTO.getCreatedAt());
    renewalReminder.setSentAt(renewalReminderDTO.getSentAt());
    renewalReminder.setCertificateType(renewalReminderDTO.getCertificateType());
    final Certificate certificate =
        renewalReminderDTO.getCertificate() == null
            ? null
            : certificateRepository
                .findById(renewalReminderDTO.getCertificate())
                .orElseThrow(() -> new NotFoundException("certificate not found"));
    renewalReminder.setCertificate(certificate);
    final User customer =
        renewalReminderDTO.getCustomer() == null
            ? null
            : userRepository
                .findById(renewalReminderDTO.getCustomer())
                .orElseThrow(() -> new NotFoundException("customer not found"));
    renewalReminder.setCustomer(customer);
    final Notification notification =
        renewalReminderDTO.getNotification() == null
            ? null
            : notificationRepository
                .findById(renewalReminderDTO.getNotification())
                .orElseThrow(() -> new NotFoundException("notification not found"));
    renewalReminder.setNotification(notification);
    final Property property =
        renewalReminderDTO.getProperty() == null
            ? null
            : propertyRepository
                .findById(renewalReminderDTO.getProperty())
                .orElseThrow(() -> new NotFoundException("property not found"));
    renewalReminder.setProperty(property);
    return renewalReminder;
  }

  @EventListener(BeforeDeleteCertificate.class)
  public void on(final BeforeDeleteCertificate event) {
    final ReferencedException referencedException = new ReferencedException();
    final RenewalReminder certificateRenewalReminder =
        renewalReminderRepository.findFirstByCertificateId(event.getId());
    if (certificateRenewalReminder != null) {
      referencedException.setKey("certificate.renewalReminder.certificate.referenced");
      referencedException.addParam(certificateRenewalReminder.getId());
      throw referencedException;
    }
  }

  @EventListener(BeforeDeleteUser.class)
  public void on(final BeforeDeleteUser event) {
    final ReferencedException referencedException = new ReferencedException();
    final RenewalReminder customerRenewalReminder =
        renewalReminderRepository.findFirstByCustomerId(event.getId());
    if (customerRenewalReminder != null) {
      referencedException.setKey("user.renewalReminder.customer.referenced");
      referencedException.addParam(customerRenewalReminder.getId());
      throw referencedException;
    }
  }

  @EventListener(BeforeDeleteNotification.class)
  public void on(final BeforeDeleteNotification event) {
    final ReferencedException referencedException = new ReferencedException();
    final RenewalReminder notificationRenewalReminder =
        renewalReminderRepository.findFirstByNotificationId(event.getId());
    if (notificationRenewalReminder != null) {
      referencedException.setKey("notification.renewalReminder.notification.referenced");
      referencedException.addParam(notificationRenewalReminder.getId());
      throw referencedException;
    }
  }

  @EventListener(BeforeDeleteProperty.class)
  public void on(final BeforeDeleteProperty event) {
    final ReferencedException referencedException = new ReferencedException();
    final RenewalReminder propertyRenewalReminder =
        renewalReminderRepository.findFirstByPropertyId(event.getId());
    if (propertyRenewalReminder != null) {
      referencedException.setKey("property.renewalReminder.property.referenced");
      referencedException.addParam(propertyRenewalReminder.getId());
      throw referencedException;
    }
  }
}
