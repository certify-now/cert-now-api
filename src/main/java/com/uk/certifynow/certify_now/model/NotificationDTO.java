package com.uk.certifynow.certify_now.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class NotificationDTO {

    private UUID id;

    @NotNull
    private OffsetDateTime createdAt;

    private OffsetDateTime deliveredAt;

    private OffsetDateTime readAt;

    private OffsetDateTime sentAt;

    @Size(max = 50)
    private String category;

    @NotNull
    private String body;

    @NotNull
    @Size(max = 255)
    private String channel;

    private String failedReason;

    @Size(max = 255)
    private String firebaseMessageId;

    @Size(max = 255)
    private String sendgridMessageId;

    @NotNull
    @Size(max = 255)
    private String status;

    @NotNull
    @Size(max = 255)
    private String title;

    @Size(max = 255)
    private String twilioMessageSid;

    private String dataPayload;

    private UUID relatedJob;

    @NotNull
    private UUID user;

}
