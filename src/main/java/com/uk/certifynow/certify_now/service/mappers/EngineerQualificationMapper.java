package com.uk.certifynow.certify_now.service.mappers;

import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.domain.EngineerQualification;
import com.uk.certifynow.certify_now.model.EngineerQualificationDTO;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface EngineerQualificationMapper {

  @Mapping(target = "engineerProfile", source = "engineerProfile.id")
  EngineerQualificationDTO toDTO(EngineerQualification entity);

  @Mapping(target = "engineerProfile", ignore = true)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "dateCreated", ignore = true)
  @Mapping(target = "lastUpdated", ignore = true)
  void updateEntity(EngineerQualificationDTO dto, @MappingTarget EngineerQualification entity);

  default UUID mapProfileToUuid(EngineerProfile profile) {
    return profile == null ? null : profile.getId();
  }
}
