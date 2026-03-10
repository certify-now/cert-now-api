package com.uk.certifynow.certify_now.service.mappers;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Review;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.model.ReviewDTO;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface ReviewMapper {

  @Mapping(target = "job", source = "job.id")
  @Mapping(target = "reviewee", source = "reviewee.id")
  @Mapping(target = "reviewer", source = "reviewer.id")
  ReviewDTO toDTO(Review entity);

  @Mapping(target = "job", ignore = true)
  @Mapping(target = "reviewee", ignore = true)
  @Mapping(target = "reviewer", ignore = true)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "dateCreated", ignore = true)
  @Mapping(target = "lastUpdated", ignore = true)
  void updateEntity(ReviewDTO dto, @MappingTarget Review entity);

  default UUID mapUserToUuid(User user) {
    return user == null ? null : user.getId();
  }

  default UUID mapJobToUuid(Job job) {
    return job == null ? null : job.getId();
  }
}
