package com.uk.certifynow.certify_now.service.mappers;

import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.model.PropertyDTO;
import java.util.UUID;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface PropertyMapper {

  @Mapping(target = "owner", source = "owner.id")
  @Mapping(
      target = "hasGasCertPdf",
      expression = "java(property.getGasCertPdf() != null && property.getGasCertPdf().length > 0)")
  @Mapping(
      target = "hasEicrCertPdf",
      expression =
          "java(property.getEicrCertPdf() != null && property.getEicrCertPdf().length > 0)")
  // Computed compliance fields are populated by ComplianceService, not here
  @Mapping(target = "gasStatus", ignore = true)
  @Mapping(target = "eicrStatus", ignore = true)
  @Mapping(target = "gasDaysUntilExpiry", ignore = true)
  @Mapping(target = "eicrDaysUntilExpiry", ignore = true)
  @Mapping(target = "nextActions", ignore = true)
  @Mapping(target = "gasCertPdfBytes", ignore = true)
  @Mapping(target = "eicrCertPdfBytes", ignore = true)
  PropertyDTO toDTO(Property property);

  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "propertyCertificates", ignore = true)
  @Mapping(target = "propertyJobs", ignore = true)
  @Mapping(target = "propertyRenewalReminders", ignore = true)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "gasCertPdf", ignore = true)
  @Mapping(target = "eicrCertPdf", ignore = true)
  Property toEntity(PropertyDTO dto);

  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "propertyCertificates", ignore = true)
  @Mapping(target = "propertyJobs", ignore = true)
  @Mapping(target = "propertyRenewalReminders", ignore = true)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "gasCertPdf", ignore = true)
  @Mapping(target = "eicrCertPdf", ignore = true)
  // Server-managed fields — must not be overwritten by incoming DTO
  @Mapping(target = "isActive", ignore = true)
  @Mapping(target = "complianceStatus", ignore = true)
  @Mapping(target = "gasCertPdfName", ignore = true)
  @Mapping(target = "eicrCertPdfName", ignore = true)
  void updateEntity(PropertyDTO dto, @MappingTarget Property property);

  @AfterMapping
  default void setDefaultComplianceStatus(PropertyDTO dto, @MappingTarget Property property) {
    if (property.getComplianceStatus() == null || property.getComplianceStatus().trim().isEmpty()) {
      property.setComplianceStatus("PENDING");
    }
  }

  default UUID mapUserToUuid(User user) {
    return user == null ? null : user.getId();
  }
}
