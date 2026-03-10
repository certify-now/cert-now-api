package com.uk.certifynow.certify_now.service.mappers;

import com.uk.certifynow.certify_now.domain.CustomerProfile;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.model.CustomerProfileDTO;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface CustomerProfileMapper {

  @Mapping(target = "user", source = "user.id")
  CustomerProfileDTO toDTO(CustomerProfile entity);

  @Mapping(target = "user", ignore = true)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "dateCreated", ignore = true)
  @Mapping(target = "lastUpdated", ignore = true)
  CustomerProfile toEntity(CustomerProfileDTO dto);

  @Mapping(target = "user", ignore = true)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "dateCreated", ignore = true)
  @Mapping(target = "lastUpdated", ignore = true)
  void updateEntity(CustomerProfileDTO dto, @MappingTarget CustomerProfile entity);

  default UUID mapUserToUuid(User user) {
    return user == null ? null : user.getId();
  }
}
