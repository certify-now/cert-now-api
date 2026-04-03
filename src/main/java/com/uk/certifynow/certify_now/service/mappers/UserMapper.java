package com.uk.certifynow.certify_now.service.mappers;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.model.UserDTO;
import com.uk.certifynow.certify_now.model.UserMeDTO;
import com.uk.certifynow.certify_now.service.enums.AuthProvider;
import com.uk.certifynow.certify_now.service.enums.UserRole;
import com.uk.certifynow.certify_now.service.enums.UserStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(imports = {AuthProvider.class, UserRole.class, UserStatus.class})
public interface UserMapper {

  @Mapping(target = "authProvider", expression = "java(user.getAuthProvider().name())")
  @Mapping(target = "role", expression = "java(user.getRole().name())")
  @Mapping(target = "status", expression = "java(user.getStatus().name())")
  UserDTO toDTO(User user);

  @Mapping(target = "role", expression = "java(user.getRole().name())")
  @Mapping(target = "status", expression = "java(user.getStatus().name())")
  UserMeDTO toMeDTO(User user);

  @Mapping(
      target = "authProvider",
      expression = "java(AuthProvider.valueOf(dto.getAuthProvider()))")
  @Mapping(target = "role", expression = "java(UserRole.valueOf(dto.getRole()))")
  @Mapping(target = "status", expression = "java(UserStatus.valueOf(dto.getStatus()))")
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "ownerProperties", ignore = true)
  @Mapping(target = "userRefreshTokens", ignore = true)
  void updateEntity(UserDTO dto, @MappingTarget User user);
}
