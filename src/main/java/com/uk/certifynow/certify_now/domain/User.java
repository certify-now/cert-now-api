package com.uk.certifynow.certify_now.domain;

import com.uk.certifynow.certify_now.service.auth.AuthProvider;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import com.uk.certifynow.certify_now.service.auth.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "\"user\"")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class User implements SoftDeletable {

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

  @OneToMany(mappedBy = "owner")
  private Set<Property> ownerProperties = new HashSet<>();

  @OneToMany(mappedBy = "user")
  private Set<RefreshToken> userRefreshTokens = new HashSet<>();

  // ── Soft-delete fields ──────────────────────────────────────────────────

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  @Column(name = "deleted_by")
  private UUID deletedBy;

  // Domain behavior methods

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
