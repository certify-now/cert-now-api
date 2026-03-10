package com.uk.certifynow.certify_now.service.mappers;

import com.uk.certifynow.certify_now.domain.EngineerAvailability;
import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.model.EngineerAvailabilityDTO;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface EngineerAvailabilityMapper {

  @Mapping(target = "engineerProfile", source = "engineerProfile.id")
  EngineerAvailabilityDTO toDTO(EngineerAvailability entity);

  @Mapping(target = "engineerProfile", ignore = true)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "dateCreated", ignore = true)
  @Mapping(target = "lastUpdated", ignore = true)
  void updateEntity(EngineerAvailabilityDTO dto, @MappingTarget EngineerAvailability entity);

  default UUID mapProfileToUuid(EngineerProfile profile) {
    return profile == null ? null : profile.getId();
  }
}
