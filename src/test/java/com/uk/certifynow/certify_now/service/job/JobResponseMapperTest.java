package com.uk.certifynow.certify_now.service.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Payment;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.rest.dto.job.DayAvailability;
import com.uk.certifynow.certify_now.rest.dto.job.JobResponse;
import com.uk.certifynow.certify_now.rest.dto.job.JobSummaryResponse;
import com.uk.certifynow.certify_now.util.TestConstants;
import com.uk.certifynow.certify_now.util.TestJobBuilder;
import com.uk.certifynow.certify_now.util.TestPropertyBuilder;
import com.uk.certifynow.certify_now.util.TestUserBuilder;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class JobResponseMapperTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  private JobResponseMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new JobResponseMapper(new ObjectMapper());
  }

  @Test
  void toJobResponse_mapsAllFields() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildMatched(customer, property, engineer);

    final JobResponse response = mapper.toJobResponse(job, null);

    assertThat(response.id()).isEqualTo(job.getId());
    assertThat(response.referenceNumber()).isEqualTo(job.getReferenceNumber());
    assertThat(response.customerId()).isEqualTo(customer.getId());
    assertThat(response.propertyId()).isEqualTo(property.getId());
    assertThat(response.engineerId()).isEqualTo(engineer.getId());
    assertThat(response.engineerName()).isEqualTo(engineer.getFullName());
    assertThat(response.certificateType()).isEqualTo("GAS_SAFETY");
    assertThat(response.status()).isEqualTo("MATCHED");
    assertThat(response.pricing()).isNotNull();
    assertThat(response.pricing().totalPricePence()).isEqualTo(9900);
  }

  @Test
  void toJobResponse_nullEngineer_handlesGracefully() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);

    final JobResponse response = mapper.toJobResponse(job, null);

    assertThat(response.engineerId()).isNull();
    assertThat(response.engineerName()).isNull();
  }

  @Test
  void toJobResponse_nullPayment_handlesGracefully() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);

    final JobResponse response = mapper.toJobResponse(job, null);

    assertThat(response.payment()).isNull();
  }

  @Test
  void toJobResponse_withPayment_mapsPaymentFields() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);
    final Payment payment = new Payment();
    payment.setId(UUID.randomUUID());
    payment.setStatus("PENDING");
    payment.setStripeClientSecret("pi_secret");
    payment.setAmountPence(9900);

    final JobResponse response = mapper.toJobResponse(job, payment);

    assertThat(response.payment()).isNotNull();
    assertThat(response.payment().status()).isEqualTo("PENDING");
    assertThat(response.payment().amountPence()).isEqualTo(9900);
  }

  @Test
  void toJobSummary_mapsAllFields() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildMatched(customer, property, engineer);

    final JobSummaryResponse summary = mapper.toJobSummary(job);

    assertThat(summary.id()).isEqualTo(job.getId());
    assertThat(summary.status()).isEqualTo("MATCHED");
    assertThat(summary.totalPricePence()).isEqualTo(9900);
    assertThat(summary.engineerName()).isEqualTo(engineer.getFullName());
  }

  @Test
  void parseAvailability_validJson_parsesList() {
    final String json = "[{\"day\":\"MON\",\"slots\":[\"MORNING\",\"AFTERNOON\"]}]";

    final List<DayAvailability> result = mapper.parseAvailability(json);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).day()).isEqualTo("MON");
    assertThat(result.get(0).slots()).containsExactly("MORNING", "AFTERNOON");
  }

  @Test
  void parseAvailability_invalidJson_returnsEmptyList() {
    final List<DayAvailability> result = mapper.parseAvailability("{not valid json}");
    assertThat(result).isEmpty();
  }

  @Test
  void parseAvailability_null_returnsEmptyList() {
    assertThat(mapper.parseAvailability(null)).isEmpty();
  }

  @Test
  void parseAvailability_blank_returnsEmptyList() {
    assertThat(mapper.parseAvailability("  ")).isEmpty();
  }
}
