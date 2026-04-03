package com.uk.certifynow.certify_now.service.notification;

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
import com.uk.certifynow.certify_now.service.mappers.NotificationMapper;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final JobRepository jobRepository;
  private final UserRepository userRepository;
  private final NotificationMapper notificationMapper;
  private final ApplicationEventPublisher publisher;

  public NotificationService(
      final NotificationRepository notificationRepository,
      final JobRepository jobRepository,
      final UserRepository userRepository,
      final NotificationMapper notificationMapper,
      final ApplicationEventPublisher publisher) {
    this.notificationRepository = notificationRepository;
    this.jobRepository = jobRepository;
    this.userRepository = userRepository;
    this.notificationMapper = notificationMapper;
    this.publisher = publisher;
  }

  public List<NotificationDTO> findAll() {
    final List<Notification> notifications = notificationRepository.findAll(Sort.by("id"));
    return notifications.stream().map(notificationMapper::toDTO).toList();
  }

  public NotificationDTO get(final UUID id) {
    return notificationRepository
        .findById(id)
        .map(notificationMapper::toDTO)
        .orElseThrow(NotFoundException::new);
  }

  @Transactional
  public UUID create(final NotificationDTO notificationDTO) {
    final Notification notification = new Notification();
    notificationMapper.updateEntity(notificationDTO, notification);
    resolveReferences(notificationDTO, notification);
    final UUID savedId = notificationRepository.save(notification).getId();
    log.info("Notification {} created", savedId);
    return savedId;
  }

  @Transactional
  public void update(final UUID id, final NotificationDTO notificationDTO) {
    final Notification notification =
        notificationRepository.findById(id).orElseThrow(NotFoundException::new);
    notificationMapper.updateEntity(notificationDTO, notification);
    resolveReferences(notificationDTO, notification);
    notificationRepository.save(notification);
    log.info("Notification {} updated", id);
  }

  @Transactional
  public void delete(final UUID id) {
    final Notification notification =
        notificationRepository.findById(id).orElseThrow(NotFoundException::new);
    publisher.publishEvent(new BeforeDeleteNotification(id));
    notificationRepository.delete(notification);
    log.info("Notification {} deleted", id);
  }

  private void resolveReferences(final NotificationDTO dto, final Notification entity) {
    final Job relatedJob =
        dto.getRelatedJob() == null
            ? null
            : jobRepository
                .findById(dto.getRelatedJob())
                .orElseThrow(() -> new NotFoundException("relatedJob not found"));
    entity.setRelatedJob(relatedJob);
    final User user =
        dto.getUser() == null
            ? null
            : userRepository
                .findById(dto.getUser())
                .orElseThrow(() -> new NotFoundException("user not found"));
    entity.setUser(user);
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
