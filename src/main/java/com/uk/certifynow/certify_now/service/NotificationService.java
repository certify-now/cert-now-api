package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Notification;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteJob;
import com.uk.certifynow.certify_now.events.BeforeDeleteNotification;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.NotificationDTO;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.NotificationRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final JobRepository jobRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher publisher;

  public NotificationService(
      final NotificationRepository notificationRepository,
      final JobRepository jobRepository,
      final UserRepository userRepository,
      final ApplicationEventPublisher publisher) {
    this.notificationRepository = notificationRepository;
    this.jobRepository = jobRepository;
    this.userRepository = userRepository;
    this.publisher = publisher;
  }

  public List<NotificationDTO> findAll() {
    final List<Notification> notifications = notificationRepository.findAll(Sort.by("id"));
    return notifications.stream()
        .map(notification -> mapToDTO(notification, new NotificationDTO()))
        .toList();
  }

  public NotificationDTO get(final UUID id) {
    return notificationRepository
        .findById(id)
        .map(notification -> mapToDTO(notification, new NotificationDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public UUID create(final NotificationDTO notificationDTO) {
    final Notification notification = new Notification();
    mapToEntity(notificationDTO, notification);
    return notificationRepository.save(notification).getId();
  }

  public void update(final UUID id, final NotificationDTO notificationDTO) {
    final Notification notification =
        notificationRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(notificationDTO, notification);
    notificationRepository.save(notification);
  }

  public void delete(final UUID id) {
    final Notification notification =
        notificationRepository.findById(id).orElseThrow(NotFoundException::new);
    publisher.publishEvent(new BeforeDeleteNotification(id));
    notificationRepository.delete(notification);
  }

  private NotificationDTO mapToDTO(
      final Notification notification, final NotificationDTO notificationDTO) {
    notificationDTO.setId(notification.getId());
    notificationDTO.setCreatedAt(notification.getCreatedAt());
    notificationDTO.setDeliveredAt(notification.getDeliveredAt());
    notificationDTO.setReadAt(notification.getReadAt());
    notificationDTO.setSentAt(notification.getSentAt());
    notificationDTO.setCategory(notification.getCategory());
    notificationDTO.setBody(notification.getBody());
    notificationDTO.setChannel(notification.getChannel());
    notificationDTO.setFailedReason(notification.getFailedReason());
    notificationDTO.setFirebaseMessageId(notification.getFirebaseMessageId());
    notificationDTO.setSendgridMessageId(notification.getSendgridMessageId());
    notificationDTO.setStatus(notification.getStatus());
    notificationDTO.setTitle(notification.getTitle());
    notificationDTO.setTwilioMessageSid(notification.getTwilioMessageSid());
    notificationDTO.setDataPayload(notification.getDataPayload());
    notificationDTO.setRelatedJob(
        notification.getRelatedJob() == null ? null : notification.getRelatedJob().getId());
    notificationDTO.setUser(notification.getUser() == null ? null : notification.getUser().getId());
    return notificationDTO;
  }

  private Notification mapToEntity(
      final NotificationDTO notificationDTO, final Notification notification) {
    notification.setCreatedAt(notificationDTO.getCreatedAt());
    notification.setDeliveredAt(notificationDTO.getDeliveredAt());
    notification.setReadAt(notificationDTO.getReadAt());
    notification.setSentAt(notificationDTO.getSentAt());
    notification.setCategory(notificationDTO.getCategory());
    notification.setBody(notificationDTO.getBody());
    notification.setChannel(notificationDTO.getChannel());
    notification.setFailedReason(notificationDTO.getFailedReason());
    notification.setFirebaseMessageId(notificationDTO.getFirebaseMessageId());
    notification.setSendgridMessageId(notificationDTO.getSendgridMessageId());
    notification.setStatus(notificationDTO.getStatus());
    notification.setTitle(notificationDTO.getTitle());
    notification.setTwilioMessageSid(notificationDTO.getTwilioMessageSid());
    notification.setDataPayload(notificationDTO.getDataPayload());
    final Job relatedJob =
        notificationDTO.getRelatedJob() == null
            ? null
            : jobRepository
                .findById(notificationDTO.getRelatedJob())
                .orElseThrow(() -> new NotFoundException("relatedJob not found"));
    notification.setRelatedJob(relatedJob);
    final User user =
        notificationDTO.getUser() == null
            ? null
            : userRepository
                .findById(notificationDTO.getUser())
                .orElseThrow(() -> new NotFoundException("user not found"));
    notification.setUser(user);
    return notification;
  }

  @EventListener(BeforeDeleteJob.class)
  public void on(final BeforeDeleteJob event) {
    final ReferencedException referencedException = new ReferencedException();
    final Notification relatedJobNotification =
        notificationRepository.findFirstByRelatedJobId(event.getId());
    if (relatedJobNotification != null) {
      referencedException.setKey("job.notification.relatedJob.referenced");
      referencedException.addParam(relatedJobNotification.getId());
      throw referencedException;
    }
  }

  @EventListener(BeforeDeleteUser.class)
  public void on(final BeforeDeleteUser event) {
    final ReferencedException referencedException = new ReferencedException();
    final Notification userNotification = notificationRepository.findFirstByUserId(event.getId());
    if (userNotification != null) {
      referencedException.setKey("user.notification.user.referenced");
      referencedException.addParam(userNotification.getId());
      throw referencedException;
    }
  }
}
