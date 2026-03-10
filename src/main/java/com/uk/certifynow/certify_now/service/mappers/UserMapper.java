package com.uk.certifynow.certify_now.service.mappers;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.model.UserDTO;
import com.uk.certifynow.certify_now.model.UserMeDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
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
  @Mapping(target = "issuedByEngineerCertificates", ignore = true)
  @Mapping(target = "userCustomerProfiles", ignore = true)
  @Mapping(target = "userDataRequests", ignore = true)
  @Mapping(target = "ownerDocuments", ignore = true)
  @Mapping(target = "userEngineerProfiles", ignore = true)
  @Mapping(target = "engineerJobMatchLogs", ignore = true)
  @Mapping(target = "customerJobs", ignore = true)
  @Mapping(target = "engineerJobs", ignore = true)
  @Mapping(target = "senderMessages", ignore = true)
  @Mapping(target = "userNotifications", ignore = true)
  @Mapping(target = "customerPayments", ignore = true)
  @Mapping(target = "engineerPayouts", ignore = true)
  @Mapping(target = "ownerProperties", ignore = true)
  @Mapping(target = "userRefreshTokens", ignore = true)
  @Mapping(target = "customerRenewalReminders", ignore = true)
  @Mapping(target = "revieweeReviews", ignore = true)
  @Mapping(target = "reviewerReviews", ignore = true)
  @Mapping(target = "userUserConsents", ignore = true)
  @Mapping(target = "dateCreated", ignore = true)
  @Mapping(target = "lastUpdated", ignore = true)
  void updateEntity(UserDTO dto, @MappingTarget User user);
}
