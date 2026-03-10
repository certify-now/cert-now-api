package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Notification;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteJob;
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

  public NotificationService(
      final NotificationRepository notificationRepository,
      final JobRepository jobRepository,
      final UserRepository userRepository,
      final NotificationMapper notificationMapper) {
    this.notificationRepository = notificationRepository;
    this.jobRepository = jobRepository;
    this.userRepository = userRepository;
    this.notificationMapper = notificationMapper;
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
    UUID savedId = notificationRepository.save(notification).getId();
    log.info(
        "Notification {} created (type={}) for user {}",
        savedId,
        notificationDTO.getNotificationType(),
        notificationDTO.getUser());
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
    notificationRepository.findById(id).orElseThrow(NotFoundException::new);
    notificationRepository.deleteById(id);
    log.info("Notification {} deleted", id);
  }

  private void resolveReferences(
      final NotificationDTO notificationDTO, final Notification notification) {
    final Job job =
        notificationDTO.getJob() == null
            ? null
            : jobRepository
                .findById(notificationDTO.getJob())
                .orElseThrow(() -> new NotFoundException("job not found"));
    notification.setJob(job);
    final User user =
        notificationDTO.getUser() == null
            ? null
            : userRepository
                .findById(notificationDTO.getUser())
                .orElseThrow(() -> new NotFoundException("user not found"));
    notification.setUser(user);
  }

  @EventListener(BeforeDeleteJob.class)
  public void on(final BeforeDeleteJob event) {
    final ReferencedException referencedException = new ReferencedException();
    final Notification jobNotification = notificationRepository.findFirstByJobId(event.getId());
    if (jobNotification != null) {
      referencedException.setKey("job.notification.job.referenced");
      referencedException.addParam(jobNotification.getId());
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
