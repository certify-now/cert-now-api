package com.uk.certifynow.certify_now.service.mappers;

import com.uk.certifynow.certify_now.domain.EngineerInsurance;
import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.model.EngineerInsuranceDTO;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface EngineerInsuranceMapper {

  @Mapping(target = "engineerProfile", source = "engineerProfile.id")
  EngineerInsuranceDTO toDTO(EngineerInsurance entity);

  @Mapping(target = "engineerProfile", ignore = true)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "dateCreated", ignore = true)
  @Mapping(target = "lastUpdated", ignore = true)
  void updateEntity(EngineerInsuranceDTO dto, @MappingTarget EngineerInsurance entity);

  default UUID mapProfileToUuid(EngineerProfile profile) {
    return profile == null ? null : profile.getId();
  }
}
