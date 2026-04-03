package com.uk.certifynow.certify_now.service.mappers;

import com.uk.certifynow.certify_now.domain.Certificate;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.model.PropertyDTO;
import com.uk.certifynow.certify_now.rest.dto.property.CreatePropertyRequest;
import com.uk.certifynow.certify_now.rest.dto.property.UpdatePropertyRequest;
import com.uk.certifynow.certify_now.service.enums.ComplianceStatus;
import java.util.UUID;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface PropertyMapper {

  @Mapping(target = "owner", source = "owner.id")
  @Mapping(target = "currentGasCertificateId", source = "currentGasCertificate.id")
  @Mapping(target = "currentEicrCertificateId", source = "currentEicrCertificate.id")
  @Mapping(target = "currentEpcCertificateId", source = "currentEpcCertificate.id")
  // EPC data mapped directly from the current EPC certificate entity
  @Mapping(target = "epcRating", source = "currentEpcCertificate.epcRating")
  @Mapping(target = "epcExpiryDate", source = "currentEpcCertificate.expiryAt")
  // Computed compliance fields are populated by ComplianceService, not here
  @Mapping(target = "gasStatus", ignore = true)
  @Mapping(target = "eicrStatus", ignore = true)
  @Mapping(target = "gasDaysUntilExpiry", ignore = true)
  @Mapping(target = "eicrDaysUntilExpiry", ignore = true)
  @Mapping(target = "epcStatus", ignore = true)
  @Mapping(target = "epcDaysUntilExpiry", ignore = true)
  @Mapping(target = "nextActions", ignore = true)
  PropertyDTO toDTO(Property property);

  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "certificates", ignore = true)
  @Mapping(target = "complianceDocuments", ignore = true)
  @Mapping(target = "propertyJobs", ignore = true)
  @Mapping(target = "propertyRenewalReminders", ignore = true)
  @Mapping(target = "currentGasCertificate", ignore = true)
  @Mapping(target = "currentEicrCertificate", ignore = true)
  @Mapping(target = "currentEpcCertificate", ignore = true)
  @Mapping(target = "coordinates", ignore = true)
  @Mapping(target = "id", ignore = true)
  Property toEntity(PropertyDTO dto);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "isActive", ignore = true)
  @Mapping(target = "complianceStatus", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  @Mapping(target = "deletedBy", ignore = true)
  @Mapping(target = "epcRegisterRef", ignore = true)
  @Mapping(target = "coordinates", ignore = true)
  @Mapping(target = "hasGasCertificate", ignore = true)
  @Mapping(target = "gasExpiryDate", ignore = true)
  @Mapping(target = "hasEicr", ignore = true)
  @Mapping(target = "eicrExpiryDate", ignore = true)
  @Mapping(target = "currentGasCertificate", ignore = true)
  @Mapping(target = "currentEicrCertificate", ignore = true)
  @Mapping(target = "currentEpcCertificate", ignore = true)
  @Mapping(target = "certificates", ignore = true)
  @Mapping(target = "complianceDocuments", ignore = true)
  @Mapping(target = "propertyJobs", ignore = true)
  @Mapping(target = "propertyRenewalReminders", ignore = true)
  Property toEntity(CreatePropertyRequest request);

  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "certificates", ignore = true)
  @Mapping(target = "complianceDocuments", ignore = true)
  @Mapping(target = "propertyJobs", ignore = true)
  @Mapping(target = "propertyRenewalReminders", ignore = true)
  @Mapping(target = "currentGasCertificate", ignore = true)
  @Mapping(target = "currentEicrCertificate", ignore = true)
  @Mapping(target = "currentEpcCertificate", ignore = true)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "coordinates", ignore = true)
  // Server-managed fields — must not be overwritten by incoming DTO
  @Mapping(target = "isActive", ignore = true)
  @Mapping(target = "complianceStatus", ignore = true)
  void updateEntity(PropertyDTO dto, @MappingTarget Property property);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "isActive", ignore = true)
  @Mapping(target = "complianceStatus", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  @Mapping(target = "deletedBy", ignore = true)
  @Mapping(target = "uprn", ignore = true)
  @Mapping(target = "epcRegisterRef", ignore = true)
  @Mapping(target = "coordinates", ignore = true)
  @Mapping(target = "hasGasCertificate", ignore = true)
  @Mapping(target = "gasExpiryDate", ignore = true)
  @Mapping(target = "hasEicr", ignore = true)
  @Mapping(target = "eicrExpiryDate", ignore = true)
  @Mapping(target = "currentGasCertificate", ignore = true)
  @Mapping(target = "currentEicrCertificate", ignore = true)
  @Mapping(target = "currentEpcCertificate", ignore = true)
  @Mapping(target = "certificates", ignore = true)
  @Mapping(target = "complianceDocuments", ignore = true)
  @Mapping(target = "propertyJobs", ignore = true)
  @Mapping(target = "propertyRenewalReminders", ignore = true)
  void updateEntity(UpdatePropertyRequest request, @MappingTarget Property property);

  @AfterMapping
  default void setDefaultComplianceStatus(PropertyDTO dto, @MappingTarget Property property) {
    if (property.getComplianceStatus() == null || property.getComplianceStatus().trim().isEmpty()) {
      property.setComplianceStatus(ComplianceStatus.MISSING.name());
    }
  }

  default UUID mapUserToUuid(User user) {
    return user == null ? null : user.getId();
  }

  default UUID mapCertificateToUuid(Certificate certificate) {
    return certificate == null ? null : certificate.getId();
  }
}
