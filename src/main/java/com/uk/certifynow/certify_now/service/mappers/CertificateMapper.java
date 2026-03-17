package com.uk.certifynow.certify_now.service.mappers;

import com.uk.certifynow.certify_now.domain.Certificate;
import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.model.CertificateDTO;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface CertificateMapper {

  @Mapping(target = "issuedByEngineer", source = "issuedByEngineer.id")
  @Mapping(target = "job", source = "job.id")
  @Mapping(target = "property", source = "property.id")
  @Mapping(target = "supersededBy", source = "supersededBy.id")
  CertificateDTO toDTO(Certificate entity);

  @Mapping(target = "issuedByEngineer", ignore = true)
  @Mapping(target = "job", ignore = true)
  @Mapping(target = "property", ignore = true)
  @Mapping(target = "supersededBy", ignore = true)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "supersededByCertificates", ignore = true)
  @Mapping(target = "certificateRenewalReminders", ignore = true)
  void updateEntity(CertificateDTO dto, @MappingTarget Certificate entity);

  default UUID mapUserToUuid(User user) {
    return user == null ? null : user.getId();
  }

  default UUID mapJobToUuid(Job job) {
    return job == null ? null : job.getId();
  }

  default UUID mapPropertyToUuid(Property property) {
    return property == null ? null : property.getId();
  }

  default UUID mapCertificateToUuid(Certificate certificate) {
    return certificate == null ? null : certificate.getId();
  }
}
