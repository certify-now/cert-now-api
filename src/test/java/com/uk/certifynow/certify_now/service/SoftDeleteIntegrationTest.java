package com.uk.certifynow.certify_now.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uk.certifynow.certify_now.domain.CustomerProfile;
import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Payment;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.repos.CustomerProfileRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.PaymentRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.scheduler.GdprDataPurgeScheduler;
import com.uk.certifynow.certify_now.service.auth.AuthProvider;
import com.uk.certifynow.certify_now.service.auth.EngineerApplicationStatus;
import com.uk.certifynow.certify_now.service.auth.EngineerTier;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import com.uk.certifynow.certify_now.service.auth.UserStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for the soft-delete functionality across User, Property, EngineerProfile, and
 * CustomerProfile entities.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class SoftDeleteIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("certify_now_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private UserRepository userRepository;
  @Autowired private PropertyRepository propertyRepository;
  @Autowired private CustomerProfileRepository customerProfileRepository;
  @Autowired private EngineerProfileRepository engineerProfileRepository;
  @Autowired private JobRepository jobRepository;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private UserService userService;
  @Autowired private PropertyService propertyService;
  @Autowired private GdprDataPurgeScheduler gdprDataPurgeScheduler;
  @Autowired private JdbcTemplate jdbcTemplate;

  private User admin;

  @BeforeEach
  void setUp() {
    // Use native SQL with TRUNCATE CASCADE to bypass @SQLRestriction and FK constraints.
    // Repository.deleteAll() won't see soft-deleted rows due to @SQLRestriction, leaving
    // orphaned data that causes FK violations on subsequent runs.
    jdbcTemplate.execute(
        "TRUNCATE TABLE payment, payout, job_status_history, job_match_log, job, "
            + "property, customer_profile, engineer_profile, refresh_token, "
            + "\"user\" CASCADE");

    admin = createUser("admin@test.com", "Test Admin", UserRole.ADMIN);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // 1. USER SOFT DELETE
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("User Soft Delete")
  class UserSoftDeleteTests {

    @Test
    @DisplayName("Soft-deleted user is excluded from normal findById queries")
    void softDeletedUserExcludedFromNormalQueries() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      createCustomerProfile(customer);
      final UUID customerId = customer.getId();

      userService.softDelete(customerId, admin.getId());

      // Standard findById should NOT find the soft-deleted user (due to @SQLRestriction)
      final Optional<User> found = userRepository.findById(customerId);
      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Soft-deleted user accessible via admin findByIdIncludingDeleted")
    void softDeletedUserAccessibleViaAdminQuery() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      createCustomerProfile(customer);
      final UUID customerId = customer.getId();

      userService.softDelete(customerId, admin.getId());

      // Admin native query should still find the user
      final Optional<User> found = userRepository.findByIdIncludingDeleted(customerId);
      assertThat(found).isPresent();
      assertThat(found.get().getDeletedAt()).isNotNull();
      assertThat(found.get().getDeletedBy()).isEqualTo(admin.getId());
    }

    @Test
    @DisplayName("Restore soft-deleted user makes it visible again")
    void restoreSoftDeletedUser() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      createCustomerProfile(customer);
      final UUID customerId = customer.getId();

      userService.softDelete(customerId, admin.getId());
      assertThat(userRepository.findById(customerId)).isEmpty();

      userService.restore(customerId, admin.getId());

      final Optional<User> found = userRepository.findById(customerId);
      assertThat(found).isPresent();
      assertThat(found.get().getDeletedAt()).isNull();
      assertThat(found.get().getDeletedBy()).isNull();
      assertThat(found.get().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Cannot soft-delete already soft-deleted user")
    void cannotDoubleSoftDelete() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      createCustomerProfile(customer);
      final UUID customerId = customer.getId();

      userService.softDelete(customerId, admin.getId());

      assertThatThrownBy(() -> userService.softDelete(customerId, admin.getId()))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("already soft-deleted");
    }

    @Test
    @DisplayName("Cannot restore user that is not soft-deleted")
    void cannotRestoreActiveUser() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      final UUID customerId = customer.getId();

      assertThatThrownBy(() -> userService.restore(customerId, admin.getId()))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("not soft-deleted");
    }

    @Test
    @DisplayName("Soft-deleted user status is set to DEACTIVATED")
    void softDeleteSetsStatusToDeactivated() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      createCustomerProfile(customer);
      final UUID customerId = customer.getId();

      userService.softDelete(customerId, admin.getId());

      final User deleted = userRepository.findByIdIncludingDeleted(customerId).orElseThrow();
      assertThat(deleted.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // 2. PROPERTY SOFT DELETE
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Property Soft Delete")
  class PropertySoftDeleteTests {

    @Test
    @DisplayName("Soft-deleted property is excluded from normal findById queries")
    void softDeletedPropertyExcludedFromNormalQueries() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      final UUID propertyId = createProperty(customer);

      propertyService.softDelete(propertyId, admin.getId());

      final Optional<Property> found = propertyRepository.findById(propertyId);
      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Soft-deleted property accessible via admin query")
    void softDeletedPropertyAccessibleViaAdminQuery() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      final UUID propertyId = createProperty(customer);

      propertyService.softDelete(propertyId, admin.getId());

      final Optional<Property> found = propertyRepository.findByIdIncludingDeleted(propertyId);
      assertThat(found).isPresent();
      assertThat(found.get().getDeletedAt()).isNotNull();
      assertThat(found.get().getDeletedBy()).isEqualTo(admin.getId());
    }

    @Test
    @DisplayName("Restore soft-deleted property makes it visible again")
    void restoreSoftDeletedProperty() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      final UUID propertyId = createProperty(customer);

      propertyService.softDelete(propertyId, admin.getId());
      assertThat(propertyRepository.findById(propertyId)).isEmpty();

      propertyService.restore(propertyId, admin.getId());

      final Optional<Property> found = propertyRepository.findById(propertyId);
      assertThat(found).isPresent();
      assertThat(found.get().getDeletedAt()).isNull();
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // 3. CASCADING SOFT DELETE
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Cascading Soft Delete")
  class CascadingSoftDeleteTests {

    @Test
    @DisplayName("Soft-deleting customer cascades to CustomerProfile")
    void softDeleteCustomerCascadesToProfile() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      final CustomerProfile profile = createCustomerProfile(customer);
      final UUID customerId = customer.getId();
      final UUID profileId = profile.getId();

      userService.softDelete(customerId, admin.getId());

      // Profile should also be soft-deleted
      final Optional<CustomerProfile> found =
          customerProfileRepository.findByIdIncludingDeleted(profileId);
      assertThat(found).isPresent();
      assertThat(found.get().getDeletedAt()).isNotNull();
      assertThat(found.get().getDeletedBy()).isEqualTo(admin.getId());
    }

    @Test
    @DisplayName("Soft-deleting engineer cascades to EngineerProfile")
    void softDeleteEngineerCascadesToProfile() {
      final User engineer = createUser("engineer@test.com", "Engineer", UserRole.ENGINEER);
      final EngineerProfile profile = createEngineerProfile(engineer);
      final UUID engineerId = engineer.getId();
      final UUID profileId = profile.getId();

      userService.softDelete(engineerId, admin.getId());

      // Profile should also be soft-deleted
      final Optional<EngineerProfile> found =
          engineerProfileRepository.findByIdIncludingDeleted(profileId);
      assertThat(found).isPresent();
      assertThat(found.get().getDeletedAt()).isNotNull();
      assertThat(found.get().getDeletedBy()).isEqualTo(admin.getId());
      assertThat(found.get().getIsOnline()).isFalse();
    }

    @Test
    @DisplayName("Restoring user cascades restore to profile")
    void restoreUserCascadesToProfile() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      final CustomerProfile profile = createCustomerProfile(customer);
      final UUID customerId = customer.getId();
      final UUID profileId = profile.getId();

      userService.softDelete(customerId, admin.getId());
      userService.restore(customerId, admin.getId());

      // Profile should also be restored
      final Optional<CustomerProfile> found = customerProfileRepository.findById(profileId);
      assertThat(found).isPresent();
      assertThat(found.get().getDeletedAt()).isNull();
      assertThat(found.get().getDeletedBy()).isNull();
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // 4. BUSINESS RULE VALIDATION
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Business Rule Validation")
  class BusinessRuleValidationTests {

    @Test
    @DisplayName("Cannot soft-delete user with active jobs")
    void cannotSoftDeleteUserWithActiveJobs() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      createCustomerProfile(customer);
      final UUID propertyId = createProperty(customer);

      // Create a job in CREATED status (active, non-terminal)
      createJob(customer, propertyId, "CREATED");

      assertThatThrownBy(() -> userService.softDelete(customer.getId(), admin.getId()))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("active jobs");
    }

    @Test
    @DisplayName("Can soft-delete user whose jobs are all in terminal state")
    void canSoftDeleteUserWithTerminalJobs() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      createCustomerProfile(customer);
      final UUID propertyId = createProperty(customer);

      // Create a job in COMPLETED status (terminal)
      createJob(customer, propertyId, "COMPLETED");

      // Should not throw
      userService.softDelete(customer.getId(), admin.getId());

      final User deleted = userRepository.findByIdIncludingDeleted(customer.getId()).orElseThrow();
      assertThat(deleted.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("Cannot soft-delete property with active jobs")
    void cannotSoftDeletePropertyWithActiveJobs() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      final UUID propertyId = createProperty(customer);

      // Create a job in CREATED status (active, non-terminal)
      createJob(customer, propertyId, "CREATED");

      assertThatThrownBy(() -> propertyService.softDelete(propertyId, admin.getId()))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("active jobs");
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // 5. FINANCIAL RECORD INTEGRITY
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Financial Record Integrity")
  class FinancialRecordIntegrityTests {

    @Test
    @DisplayName("Payments remain queryable after user is soft-deleted")
    void paymentsRemainAfterUserSoftDeleted() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      createCustomerProfile(customer);
      final UUID propertyId = createProperty(customer);

      // Create a completed job and a payment
      final Job job = createJob(customer, propertyId, "COMPLETED");
      final Payment payment = createPayment(customer, job);

      // Soft-delete the user
      userService.softDelete(customer.getId(), admin.getId());

      // Payment should still be queryable
      final Optional<Payment> foundPayment = paymentRepository.findById(payment.getId());
      assertThat(foundPayment).isPresent();
      assertThat(foundPayment.get().getAmountPence()).isEqualTo(8500);
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // 6. AUTHENTICATION
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Authentication")
  class AuthenticationTests {

    @Test
    @DisplayName("Soft-deleted user cannot authenticate")
    void softDeletedUserCannotAuthenticate() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      createCustomerProfile(customer);

      userService.softDelete(customer.getId(), admin.getId());

      final User deleted = userRepository.findByIdIncludingDeleted(customer.getId()).orElseThrow();
      assertThat(deleted.canAuthenticate()).isFalse();
    }

    @Test
    @DisplayName("Restored user can authenticate again")
    void restoredUserCanAuthenticate() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      createCustomerProfile(customer);

      userService.softDelete(customer.getId(), admin.getId());
      userService.restore(customer.getId(), admin.getId());

      final User restored = userRepository.findById(customer.getId()).orElseThrow();
      assertThat(restored.canAuthenticate()).isTrue();
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // 7. GDPR ANONYMIZATION
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("GDPR Anonymization")
  class GdprAnonymizationTests {

    @Test
    @DisplayName("User past grace period is anonymized by GDPR scheduler")
    void userPastGracePeriodIsAnonymized() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      createCustomerProfile(customer);
      final UUID customerId = customer.getId();

      // Soft-delete and manually set deletedAt to 31 days ago (past the 30-day grace period).
      // We use jdbcTemplate to bypass @SQLRestriction / JPA persistence context issues.
      userService.softDelete(customerId, admin.getId());
      jdbcTemplate.update(
          "UPDATE \"user\" SET deleted_at = ? WHERE id = ?",
          OffsetDateTime.now().minusDays(31),
          customerId);

      // Run the GDPR purge
      gdprDataPurgeScheduler.purgeExpiredSoftDeletedUsers();

      // Verify anonymization
      final User anonymized = userRepository.findByIdIncludingDeleted(customerId).orElseThrow();
      assertThat(anonymized.getFullName()).isEqualTo("Deleted User");
      assertThat(anonymized.getEmail())
          .isEqualTo("deleted_" + customerId + "@deleted.certifynow.co.uk");
      assertThat(anonymized.getPhone()).isNull();
      assertThat(anonymized.getAvatarUrl()).isNull();
      assertThat(anonymized.getPasswordHash()).isEqualTo("ANONYMIZED");
    }

    @Test
    @DisplayName("User within grace period is NOT anonymized")
    void userWithinGracePeriodNotAnonymized() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      createCustomerProfile(customer);
      final UUID customerId = customer.getId();

      // Soft-delete (deletedAt is now, within grace period)
      userService.softDelete(customerId, admin.getId());

      // Run the GDPR purge
      gdprDataPurgeScheduler.purgeExpiredSoftDeletedUsers();

      // Verify NOT anonymized
      final User notAnonymized = userRepository.findByIdIncludingDeleted(customerId).orElseThrow();
      assertThat(notAnonymized.getFullName()).isEqualTo("Customer");
      assertThat(notAnonymized.getEmail()).isEqualTo("customer@test.com");
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // 8. ADMIN REPOSITORY QUERIES
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Admin Repository Queries")
  class AdminRepositoryQueryTests {

    @Test
    @DisplayName("findAllDeleted returns only soft-deleted users")
    void findAllDeletedReturnsOnlySoftDeletedUsers() {
      final User customer1 = createUser("customer1@test.com", "Customer 1", UserRole.CUSTOMER);
      createCustomerProfile(customer1);
      final User customer2 = createUser("customer2@test.com", "Customer 2", UserRole.CUSTOMER);

      userService.softDelete(customer1.getId(), admin.getId());

      final List<User> deletedUsers = userRepository.findAllDeleted();
      assertThat(deletedUsers).hasSize(1);
      assertThat(deletedUsers.get(0).getId()).isEqualTo(customer1.getId());
    }

    @Test
    @DisplayName("findAllIncludingDeleted returns both active and soft-deleted users")
    void findAllIncludingDeletedReturnsBoth() {
      final User customer1 = createUser("customer1@test.com", "Customer 1", UserRole.CUSTOMER);
      createCustomerProfile(customer1);
      final User customer2 = createUser("customer2@test.com", "Customer 2", UserRole.CUSTOMER);

      userService.softDelete(customer1.getId(), admin.getId());

      final List<User> allUsers = userRepository.findAllIncludingDeleted();
      // admin + customer1 (soft-deleted) + customer2
      assertThat(allUsers).hasSize(3);
    }

    @Test
    @DisplayName("findAllDeleted for properties returns only soft-deleted properties")
    void findAllDeletedPropertiesReturnsOnlySoftDeleted() {
      final User customer = createUser("customer@test.com", "Customer", UserRole.CUSTOMER);
      final UUID prop1 = createProperty(customer);
      final UUID prop2 = createProperty(customer);

      propertyService.softDelete(prop1, admin.getId());

      final List<Property> deletedProperties = propertyRepository.findAllDeleted();
      assertThat(deletedProperties).hasSize(1);
      assertThat(deletedProperties.get(0).getId()).isEqualTo(prop1);
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // HELPERS
  // ═══════════════════════════════════════════════════════════════════════════

  private User createUser(final String email, final String name, final UserRole role) {
    final User user = new User();
    user.setEmail(email);
    user.setFullName(name);
    user.setRole(role);
    user.setStatus(UserStatus.ACTIVE);
    user.setAuthProvider(AuthProvider.EMAIL);
    user.setPasswordHash("$2a$12$dummyHashForIntegrationTestOnly000000000000000000000");
    user.setEmailVerified(true);
    user.setPhoneVerified(false);
    user.setCreatedAt(OffsetDateTime.now());
    user.setUpdatedAt(OffsetDateTime.now());
    return userRepository.save(user);
  }

  private CustomerProfile createCustomerProfile(final User user) {
    final CustomerProfile profile = new CustomerProfile();
    profile.setUser(user);
    profile.setIsLettingAgent(false);
    profile.setTotalProperties(0);
    profile.setNotificationPrefs("{}");
    profile.setCreatedAt(OffsetDateTime.now());
    profile.setUpdatedAt(OffsetDateTime.now());
    return customerProfileRepository.save(profile);
  }

  private EngineerProfile createEngineerProfile(final User user) {
    final EngineerProfile profile = new EngineerProfile();
    profile.setUser(user);
    profile.setStatus(EngineerApplicationStatus.APPROVED);
    profile.setTier(EngineerTier.BRONZE);
    profile.setIsOnline(true);
    profile.setAcceptanceRate(BigDecimal.ONE);
    profile.setAvgRating(BigDecimal.valueOf(4.5));
    profile.setOnTimePercentage(BigDecimal.valueOf(95));
    profile.setTotalJobsCompleted(0);
    profile.setTotalReviews(0);
    profile.setStripeOnboarded(false);
    profile.setServiceRadiusMiles(BigDecimal.TEN);
    profile.setMaxDailyJobs(5);
    profile.setCreatedAt(OffsetDateTime.now());
    profile.setUpdatedAt(OffsetDateTime.now());
    return engineerProfileRepository.save(profile);
  }

  private UUID createProperty(final User owner) {
    final Property prop = new Property();
    prop.setOwner(owner);
    prop.setAddressLine1("10 Downing Street");
    prop.setCity("London");
    prop.setPostcode("SW1A 2AA");
    prop.setCountry("GB");
    prop.setPropertyType("HOUSE");
    prop.setComplianceStatus("COMPLIANT");
    prop.setIsActive(true);
    prop.setHasGasSupply(true);
    prop.setHasElectric(true);
    prop.setBedrooms(3);
    prop.setGasApplianceCount(2);
    prop.setCreatedAt(OffsetDateTime.now());
    prop.setUpdatedAt(OffsetDateTime.now());
    return propertyRepository.save(prop).getId();
  }

  private Job createJob(final User customer, final UUID propertyId, final String status) {
    final Property property = propertyRepository.findById(propertyId).orElseThrow();
    final Job job = new Job();
    job.setCustomer(customer);
    job.setProperty(property);
    job.setStatus(status);
    job.setCertificateType("EPC");
    job.setUrgency("STANDARD");
    job.setReferenceNumber("REF-" + UUID.randomUUID().toString().substring(0, 8));
    job.setBasePricePence(8500);
    job.setPropertyModifierPence(0);
    job.setUrgencyModifierPence(0);
    job.setDiscountPence(0);
    job.setTotalPricePence(8500);
    job.setCommissionRate(BigDecimal.valueOf(0.15));
    job.setCommissionPence(1275);
    job.setEngineerPayoutPence(7225);
    job.setMatchAttempts(0);
    job.setCreatedAt(OffsetDateTime.now());
    job.setUpdatedAt(OffsetDateTime.now());
    return jobRepository.save(job);
  }

  private Payment createPayment(final User customer, final Job job) {
    final Payment payment = new Payment();
    payment.setCustomer(customer);
    payment.setJob(job);
    payment.setAmountPence(8500);
    payment.setCurrency("GBP");
    payment.setStatus("CAPTURED");
    payment.setRequiresAction(false);
    payment.setCreatedAt(OffsetDateTime.now());
    payment.setUpdatedAt(OffsetDateTime.now());
    return paymentRepository.save(payment);
  }
}
