package com.uk.certifynow.certify_now.service.mappers;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Payment;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.model.PaymentDTO;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface PaymentMapper {

  @Mapping(target = "customer", source = "customer.id")
  @Mapping(target = "job", source = "job.id")
  PaymentDTO toDTO(Payment entity);

  @Mapping(target = "customer", ignore = true)
  @Mapping(target = "job", ignore = true)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "paymentPayouts", ignore = true)
  @Mapping(target = "dateCreated", ignore = true)
  @Mapping(target = "lastUpdated", ignore = true)
  void updateEntity(PaymentDTO dto, @MappingTarget Payment entity);

  default UUID mapUserToUuid(User user) {
    return user == null ? null : user.getId();
  }

  default UUID mapJobToUuid(Job job) {
    return job == null ? null : job.getId();
  }
}
