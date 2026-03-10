package com.uk.certifynow.certify_now.service.mappers;

import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.model.EngineerProfileDTO;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface EngineerProfileMapper {

  @Mapping(target = "status", expression = "java(entity.getStatus().name())")
  @Mapping(target = "tier", expression = "java(entity.getTier().name())")
  @Mapping(target = "user", source = "user.id")
  EngineerProfileDTO toDTO(EngineerProfile entity);

  @Mapping(
      target = "status",
      expression = "java(EngineerApplicationStatus.valueOf(dto.getStatus()))")
  @Mapping(target = "tier", expression = "java(EngineerTier.valueOf(dto.getTier()))")
  @Mapping(target = "user", ignore = true)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "engineerProfileEngineerAvailabilities", ignore = true)
  @Mapping(target = "engineerProfileEngineerInsurances", ignore = true)
  @Mapping(target = "engineerProfileEngineerQualifications", ignore = true)
  @Mapping(target = "dateCreated", ignore = true)
  @Mapping(target = "lastUpdated", ignore = true)
  void updateEntity(EngineerProfileDTO dto, @MappingTarget EngineerProfile entity);

  default UUID mapUserToUuid(User user) {
    return user == null ? null : user.getId();
  }
}
