package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.StripeWebhookEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEvent, UUID> {}
