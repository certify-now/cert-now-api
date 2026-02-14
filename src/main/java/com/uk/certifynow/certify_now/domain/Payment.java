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
public class Payment {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private Integer amountPence;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column
    private Integer refundAmountPence;

    @Column(nullable = false)
    private Boolean requiresAction;

    @Column
    private OffsetDateTime authorisedAt;

    @Column
    private OffsetDateTime capturedAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column
    private OffsetDateTime refundedAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @Column(length = 100)
    private String failureCode;

    @Column(length = 512)
    private String stripeReceiptUrl;

    @Column(length = 512)
    private String threeDsUrl;

    @Column(columnDefinition = "text")
    private String failureMessage;

    @Column(columnDefinition = "text")
    private String refundReason;

    @Column(nullable = false)
    private String status;

    @Column
    private String stripeChargeId;

    @Column
    private String stripeClientSecret;

    @Column
    private String stripePaymentIntentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @OneToMany(mappedBy = "payment")
    private Set<Payout> paymentPayouts = new HashSet<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private OffsetDateTime dateCreated;

    @LastModifiedDate
    @Column(nullable = false)
    private OffsetDateTime lastUpdated;

}
