package com.uk.certifynow.certify_now.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class PaymentDTO {

    private UUID id;

    @NotNull
    private Integer amountPence;

    @NotNull
    @Size(max = 3)
    private String currency;

    private Integer refundAmountPence;

    @NotNull
    private Boolean requiresAction;

    private OffsetDateTime authorisedAt;

    private OffsetDateTime capturedAt;

    @NotNull
    private OffsetDateTime createdAt;

    private OffsetDateTime refundedAt;

    @NotNull
    private OffsetDateTime updatedAt;

    @Size(max = 100)
    private String failureCode;

    @Size(max = 512)
    private String stripeReceiptUrl;

    @Size(max = 512)
    private String threeDsUrl;

    private String failureMessage;

    private String refundReason;

    @NotNull
    @Size(max = 255)
    private String status;

    @Size(max = 255)
    private String stripeChargeId;

    @Size(max = 255)
    private String stripeClientSecret;

    @Size(max = 255)
    private String stripePaymentIntentId;

    @NotNull
    private UUID customer;

    @NotNull
    private UUID job;

}
