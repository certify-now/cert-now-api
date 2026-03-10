package com.uk.certifynow.certify_now.service.mappers;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Notification;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.model.NotificationDTO;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface NotificationMapper {

  @Mapping(target = "relatedJob", source = "relatedJob.id")
  @Mapping(target = "user", source = "user.id")
  NotificationDTO toDTO(Notification entity);

  @Mapping(target = "relatedJob", ignore = true)
  @Mapping(target = "user", ignore = true)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "notificationRenewalReminders", ignore = true)
  @Mapping(target = "dateCreated", ignore = true)
  @Mapping(target = "lastUpdated", ignore = true)
  void updateEntity(NotificationDTO dto, @MappingTarget Notification entity);

  default UUID mapUserToUuid(User user) {
    return user == null ? null : user.getId();
  }

  default UUID mapJobToUuid(Job job) {
    return job == null ? null : job.getId();
  }
}
