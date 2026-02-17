package com.uk.certifynow.certify_now.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Notification {

  @Id
  @Column(nullable = false, updatable = false)
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  @Column private OffsetDateTime deliveredAt;

  @Column private OffsetDateTime readAt;

  @Column private OffsetDateTime sentAt;

  @Column(length = 50)
  private String category;

  @Column(nullable = false, columnDefinition = "text")
  private String body;

  @Column(nullable = false)
  private String channel;

  @Column(columnDefinition = "text")
  private String failedReason;

  @Column private String firebaseMessageId;

  @Column private String sendgridMessageId;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private String title;

  @Column private String twilioMessageSid;

  @Column(columnDefinition = "text")
  private String dataPayload;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "related_job_id")
  private Job relatedJob;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @OneToMany(mappedBy = "notification")
  private Set<RenewalReminder> notificationRenewalReminders = new HashSet<>();

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private OffsetDateTime dateCreated;

  @LastModifiedDate
  @Column(nullable = false)
  private OffsetDateTime lastUpdated;
}
