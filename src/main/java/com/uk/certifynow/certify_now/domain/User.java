package com.uk.certifynow.certify_now.domain;

import com.uk.certifynow.certify_now.service.auth.AuthProvider;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import com.uk.certifynow.certify_now.service.auth.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "\"user\"")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class User {

  @Id
  @Column(nullable = false, updatable = false)
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(nullable = false)
  private Boolean emailVerified;

  @Column(nullable = false)
  private Boolean phoneVerified;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  @Column private OffsetDateTime lastLoginAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  @Column(length = 20)
  private String phone;

  @Column(nullable = false, length = 50)
  @Convert(converter = AuthProvider.AuthProviderConverter.class)
  private AuthProvider authProvider;

  @Column(length = 512)
  private String avatarUrl;

  @Column(nullable = false)
  private String email;

  @Column private String externalAuthId;

  @Column(nullable = false)
  private String fullName;

  @Column(nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  @Convert(converter = UserRole.UserRoleConverter.class)
  private UserRole role;

  @Column(nullable = false)
  @Convert(converter = UserStatus.UserStatusConverter.class)
  private UserStatus status;

  @OneToMany(mappedBy = "issuedByEngineer")
  private Set<Certificate> issuedByEngineerCertificates = new HashSet<>();

  @OneToMany(mappedBy = "user")
  private Set<CustomerProfile> userCustomerProfiles = new HashSet<>();

  @OneToMany(mappedBy = "user")
  private Set<DataRequest> userDataRequests = new HashSet<>();

  @OneToMany(mappedBy = "owner")
  private Set<Document> ownerDocuments = new HashSet<>();

  @OneToMany(mappedBy = "user")
  private Set<EngineerProfile> userEngineerProfiles = new HashSet<>();

  @OneToMany(mappedBy = "engineer")
  private Set<JobMatchLog> engineerJobMatchLogs = new HashSet<>();

  @OneToMany(mappedBy = "customer")
  private Set<Job> customerJobs = new HashSet<>();

  @OneToMany(mappedBy = "engineer")
  private Set<Job> engineerJobs = new HashSet<>();

  @OneToMany(mappedBy = "sender")
  private Set<Message> senderMessages = new HashSet<>();

  @OneToMany(mappedBy = "user")
  private Set<Notification> userNotifications = new HashSet<>();

  @OneToMany(mappedBy = "customer")
  private Set<Payment> customerPayments = new HashSet<>();

  @OneToMany(mappedBy = "engineer")
  private Set<Payout> engineerPayouts = new HashSet<>();

  @OneToMany(mappedBy = "owner")
  private Set<Property> ownerProperties = new HashSet<>();

  @OneToMany(mappedBy = "user")
  private Set<RefreshToken> userRefreshTokens = new HashSet<>();

  @OneToMany(mappedBy = "customer")
  private Set<RenewalReminder> customerRenewalReminders = new HashSet<>();

  @OneToMany(mappedBy = "reviewee")
  private Set<Review> revieweeReviews = new HashSet<>();

  @OneToMany(mappedBy = "reviewer")
  private Set<Review> reviewerReviews = new HashSet<>();

  @OneToMany(mappedBy = "user")
  private Set<UserConsent> userUserConsents = new HashSet<>();

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private OffsetDateTime dateCreated;

  @LastModifiedDate
  @Column(nullable = false)
  private OffsetDateTime lastUpdated;

  // Domain behavior methods

  /**
   * Determines if this user can authenticate based on their account status.
   *
   * @return true if user can login, false otherwise
   */
  public boolean canAuthenticate() {
    return status != null && status.canAuthenticate();
  }

  /**
   * Determines if this user is an engineer.
   *
   * @return true if role is ENGINEER, false otherwise
   */
  public boolean isEngineer() {
    return role != null && role.isEngineer();
  }

  /**
   * Determines if this user is a customer.
   *
   * @return true if role is CUSTOMER, false otherwise
   */
  public boolean isCustomer() {
    return role != null && role.isCustomer();
  }

  /**
   * Updates the last login timestamp using the provided clock.
   *
   * @param clock the clock to use for timestamp
   */
  public void updateLastLogin(final Clock clock) {
    final OffsetDateTime now = OffsetDateTime.now(clock);
    this.lastLoginAt = now;
    this.updatedAt = now;
  }
}
