# CertifyNow — Build Roadmap

> **Where you are:** DDL ✅ → JPA Entities ✅ → Service Plan ✅ → Pattern Reference ✅
>
> **What this doc is:** The exact order to build everything, broken into 12 phases. Each phase produces something you can run and test before moving on. No phase depends on anything you haven't built yet.
>
> **Time estimate:** ~12–16 weeks solo, ~6–8 weeks with 2 developers.

---

## How to Read This

Each phase has:
- **Goal** — what you can demo at the end
- **Build checklist** — exact files to create, in order
- **Test checkpoint** — how to prove it works before moving on
- **Depends on** — which prior phases must be complete

```
Phase 1   ██████████  ← You are HERE (project skeleton)
Phase 2   ░░░░░░░░░░
Phase 3   ░░░░░░░░░░
...
Phase 12  ░░░░░░░░░░  ← Full production system
```

---

## Phase 0 — Project Skeleton (Day 1)

**Goal:** Spring Boot app that starts, connects to PostgreSQL, and runs Flyway migrations.

**Depends on:** Nothing. This is the foundation.

```
Build Checklist:
────────────────────────────────────────────────────────────
□ 1. Generate Spring Boot project (start.spring.io)
     - Java 21, Spring Boot 3.3+
     - Dependencies: Web, Data JPA, Validation, Security,
       Flyway, PostgreSQL, Redis, Actuator, Lombok (optional)

□ 2. Set up project structure:
     src/main/java/com/certifynow/
     ├── CertifyNowApplication.java
     ├── shared/
     │   ├── config/
     │   ├── domain/         ← BaseEntity, AuditableEntity
     │   ├── dto/
     │   ├── enums/          ← ALL 22 enums
     │   ├── event/          ← DomainEvent base class
     │   ├── exception/      ← BusinessException + subtypes
     │   ├── interfaces/     ← PricingCalculator, UserLookupService
     │   └── listener/       ← AuditEventListener
     ├── auth/
     ├── user/
     ├── property/
     ├── job/
     ├── certificate/
     ├── payment/
     ├── pricing/
     ├── notification/
     ├── compliance/
     ├── document/
     ├── messaging/
     └── admin/

□ 3. application.yml — PostgreSQL, Redis, Flyway config
□ 4. application-dev.yml — local dev overrides
□ 5. docker-compose.yml — PostgreSQL 16 + PostGIS + Redis
□ 6. V1__initial_schema.sql — copy your DDL as first Flyway migration
□ 7. Copy ALL entity classes from your JPA zip
□ 8. Copy ALL enums from your JPA zip
□ 9. Copy shared/ classes: BaseEntity, AuditableEntity, DomainEvent,
     BusinessException, AsyncConfig, CacheConfig
```

**Test Checkpoint:**
```bash
docker-compose up -d                    # PostgreSQL + Redis running
./mvnw spring-boot:run                  # App starts without errors
# Check logs for: "Flyway: Successfully applied 1 migration"
# Check DB:  SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';
#            → 31+ tables exist
```

**You can't move on until:** The app starts, Flyway creates all tables, and Hibernate validates the entity mappings with zero errors.

---

## Phase 1 — Auth: Register + Login + JWT (Week 1)

**Goal:** A customer can register, log in, get a JWT, and hit a protected endpoint.

**Depends on:** Phase 0

**Why first:** Every other endpoint needs authentication. Build the lock before the rooms.

```
Build Checklist:
────────────────────────────────────────────────────────────
□ 1. Repositories (simple Spring Data interfaces):
     - UserRepository
     - CustomerProfileRepository
     - EngineerProfileRepository
     - RefreshTokenRepository
     - UserConsentRepository

□ 2. Security config:
     - JwtTokenProvider — generate + validate access tokens (15 min)
     - JwtAuthenticationFilter — OncePerRequestFilter, reads Authorization header
     - SecurityConfig — @EnableWebSecurity, permit /auth/**, secure everything else
     - PasswordEncoder bean (BCrypt)

□ 3. DTOs:
     - RegisterRequest (email, password, fullName, phone, role)
     - LoginRequest (email, password)
     - AuthResponse (accessToken, refreshToken, expiresIn, user summary)

□ 4. AuthService — full implementation:
     - register() → create User + profile + consents + refresh token
     - login() → validate credentials, issue tokens, update last_login_at
     - refreshToken() → validate + rotate refresh token
     - logout() → revoke refresh token

□ 5. RefreshTokenService:
     - createToken(), findByTokenHash(), revokeToken()

□ 6. AuthController:
     - POST /api/v1/auth/register
     - POST /api/v1/auth/login
     - POST /api/v1/auth/refresh
     - POST /api/v1/auth/logout

□ 7. Events:
     - UserRegisteredEvent, UserLoggedInEvent
     - Publish from AuthService (listeners can wait — just log for now)

□ 8. GlobalExceptionHandler (@ControllerAdvice):
     - BusinessException → 400
     - EntityNotFoundException → 404
     - InvalidStateTransitionException → 409
     - AccessDeniedException → 403
     - ConstraintViolationException → 422
```

**Test Checkpoint:**
```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"sarah@test.com","password":"Test1234!","fullName":"Sarah Thompson","role":"CUSTOMER"}'
# → 201 with accessToken + refreshToken

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -d '{"email":"sarah@test.com","password":"Test1234!"}'
# → 200 with tokens

# Protected endpoint (should fail without token)
curl http://localhost:8080/api/v1/users/me
# → 401 Unauthorized

# Protected endpoint (should work with token)
curl http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <access_token>"
# → 200 with user data

# Check DB: SELECT * FROM users; → 1 row
# Check DB: SELECT * FROM customer_profiles; → 1 row
# Check DB: SELECT * FROM user_consents; → 2-3 rows
# Check DB: SELECT * FROM refresh_tokens; → 1 row
```

**You can't move on until:** Register → Login → access protected endpoint works end-to-end with valid JWTs.

---

## Phase 2 — User Profiles + Property CRUD (Week 2)

**Goal:** Customer can update their profile, add/edit/list properties. Engineer can register and fill their profile.

**Depends on:** Phase 1 (auth)

```
Build Checklist:
────────────────────────────────────────────────────────────
□ 1. Repositories:
     - PropertyRepository (with custom query: findByOwnerId + pageable)

□ 2. UserService:
     - getById(), getByEmail(), updateProfile()
     - @Cacheable on reads, @CacheEvict on writes

□ 3. CustomerProfileService:
     - getByUserId(), updateProfile(), incrementPropertyCount()

□ 4. PropertyService:
     - create(), getById(), getByOwner(), update(), deactivate()
     - Skip geocoding for now (hardcode a dummy PostGIS point)
     - Publish PropertyCreatedEvent

□ 5. Controllers:
     - GET    /api/v1/users/me                    → current user profile
     - PUT    /api/v1/users/me                    → update profile
     - POST   /api/v1/properties                  → add property
     - GET    /api/v1/properties                  → list my properties
     - GET    /api/v1/properties/{id}             → property detail
     - PUT    /api/v1/properties/{id}             → update property
     - DELETE /api/v1/properties/{id}             → soft delete

□ 6. Validation:
     - UK postcode regex validation
     - Property type must be valid enum
     - Bedrooms must be >= 0

□ 7. Event listener:
     - PropertyCreatedEvent → CustomerProfileService.incrementPropertyCount()
     (This is your first real event-driven cross-module interaction!)
```

**Test Checkpoint:**
```bash
# Add a property
curl -X POST http://localhost:8080/api/v1/properties \
  -H "Authorization: Bearer <token>" \
  -d '{"addressLine1":"14 Wilmslow Rd","addressLine2":"Flat 3",
       "city":"Manchester","postcode":"M14 5TQ","propertyType":"FLAT",
       "bedrooms":2,"hasGasSupply":true,"gasApplianceCount":3}'
# → 201 with property data

# Check: customer_profiles.total_properties = 1 (event worked!)

# List properties
curl http://localhost:8080/api/v1/properties -H "Authorization: Bearer <token>"
# → 200 with array containing the property

# Verify a different user can't see/edit Sarah's property
# → 403 Forbidden
```

---

## Phase 3 — Pricing Engine (Week 3)

**Goal:** Admin can configure prices. The system can calculate a job price given cert type + property + urgency.

**Depends on:** Phase 0 (entities only — no auth required for internal service)

**Why now:** The job booking flow (Phase 4) needs pricing. Build it first.

```
Build Checklist:
────────────────────────────────────────────────────────────
□ 1. Repositories:
     - PricingRuleRepository
     - PricingModifierRepository
     - UrgencyMultiplierRepository

□ 2. V2__seed_pricing.sql (Flyway migration):
     - Insert base pricing rules (GAS_SAFETY = 7500p, EPC = 8500p, EICR = 12000p)
     - Insert urgency multipliers (STANDARD=1.0, URGENT=1.5, EMERGENCY=2.0)
     - Insert property modifiers (3+ appliances = +2500p, etc.)

□ 3. PricingService:
     - calculatePrice() → PriceBreakdown
     - CRUD methods for admin management
     - Heavy @Cacheable — pricing data rarely changes

□ 4. PricingCalculatorImpl (implements shared PricingCalculator interface):
     - This is what JobService will inject

□ 5. Controllers (admin-only):
     - GET    /api/v1/admin/pricing/rules
     - POST   /api/v1/admin/pricing/rules
     - GET    /api/v1/admin/pricing/urgency-multipliers
     - PUT    /api/v1/admin/pricing/urgency-multipliers/{id}

□ 6. Test endpoint (temporary — remove later):
     - GET /api/v1/pricing/calculate?certType=GAS_SAFETY&propertyId=X&urgency=STANDARD
     - Returns PriceBreakdown so you can verify calculations
```

**Test Checkpoint:**
```bash
# Calculate a price
curl "http://localhost:8080/api/v1/pricing/calculate?certType=GAS_SAFETY&propertyId=<id>&urgency=STANDARD"
# → {"basePricePence":7500,"propertyModifierPence":2500,"urgencyModifierPence":0,
#    "totalPricePence":10000,"commissionPence":1500,"engineerPayoutPence":8500}

# Verify caching: second call should be faster (check Redis or logs)
```

---

## Phase 4 — Job Booking + State Machine (Week 4–5) ⭐ CORE

**Goal:** Customer can book a job. The job moves through CREATED → MATCHED → ACCEPTED → EN_ROUTE → IN_PROGRESS → COMPLETED. All state transitions are validated and recorded.

**Depends on:** Phase 1 (auth), Phase 2 (properties), Phase 3 (pricing)

**This is the heart of the application.** Take two weeks.

```
Build Checklist:
────────────────────────────────────────────────────────────
□ 1. Repositories:
     - JobRepository (custom queries: byCustomer, byEngineer, byStatus)
     - JobStatusHistoryRepository
     - JobMatchLogRepository
     - PaymentRepository (basic — Stripe integration comes later)

□ 2. JobService — FULL implementation (follow the pattern reference):
     - createJob() — pricing + job + payment + status history + event
     - matchJob() — assign engineer + match log
     - acceptJob() — schedule date/time
     - declineJob() — revert to CREATED
     - markEnRoute()
     - startJob() — with GPS validation (stub distance calc for now)
     - completeJob()
     - certifyJob()
     - cancelJob()
     - Every method: validate transition → apply → history → save → evict → publish

□ 3. Events (all 6 job events from the pattern reference):
     - JobCreatedEvent, JobMatchedEvent, JobAcceptedEvent,
       JobStatusChangedEvent, JobCertifiedEvent, JobCancelledEvent

□ 4. Listeners — start with LOGGING ONLY:
     - Create stub listeners that just log.info() the event
     - You'll wire in real notification/payment logic later
     - This proves the event bus works

□ 5. Controllers:
     - POST   /api/v1/jobs                        → book a job
     - GET    /api/v1/jobs                         → my jobs (customer or engineer)
     - GET    /api/v1/jobs/{id}                    → job detail
     - PUT    /api/v1/jobs/{id}/accept             → engineer accepts
     - PUT    /api/v1/jobs/{id}/decline            → engineer declines
     - PUT    /api/v1/jobs/{id}/en-route           → engineer heading there
     - PUT    /api/v1/jobs/{id}/start              → engineer starts (GPS)
     - PUT    /api/v1/jobs/{id}/complete           → engineer finishes
     - PUT    /api/v1/jobs/{id}/cancel             → cancel (any party)
     - GET    /api/v1/jobs/{id}/history            → status history trail

□ 6. Write tests for the state machine:
     - CREATED → MATCHED ✓
     - CREATED → COMPLETED ✗ (should throw InvalidStateTransitionException)
     - CANCELLED from every allowed state
     - Decline resets to CREATED
     - Double-transition prevention (accept twice → error)
```

**Test Checkpoint — walk the full happy path manually:**
```bash
# 1. Register as customer (Phase 1)
# 2. Add property (Phase 2)
# 3. Book a gas safety job
curl -X POST http://localhost:8080/api/v1/jobs \
  -H "Authorization: Bearer <customer_token>" \
  -d '{"propertyId":"<uuid>","certificateType":"GAS_SAFETY","urgency":"STANDARD"}'
# → 201, status = CREATED, pricing fields populated

# 4. Register as engineer (Phase 1 — second user)
# 5. Simulate matching (call matchJob directly or use test endpoint)
# 6. Accept as engineer
curl -X PUT http://localhost:8080/api/v1/jobs/<jobId>/accept \
  -H "Authorization: Bearer <engineer_token>" \
  -d '{"scheduledDate":"2025-06-18","scheduledTimeSlot":"MORNING"}'
# → status = ACCEPTED

# 7. Mark en route → 8. Start → 9. Complete
# 10. Check job_status_history: should have 6+ rows tracking every transition

# 11. Try invalid transition:
curl -X PUT http://localhost:8080/api/v1/jobs/<jobId>/accept ...
# → 409 Conflict (already COMPLETED, can't accept again)
```

**You can't move on until:** You can walk a job from CREATED to COMPLETED, see the full status history, and invalid transitions are rejected with 409.

---

## Phase 5 — Engineer Onboarding (Week 5–6)

**Goal:** Engineer can upload qualifications and insurance, set availability, and get approved by admin.

**Depends on:** Phase 1 (auth), Phase 4 (so engineers can receive jobs)

```
Build Checklist:
────────────────────────────────────────────────────────────
□ 1. Repositories:
     - EngineerQualificationRepository
     - EngineerInsuranceRepository
     - EngineerAvailabilityRepository

□ 2. EngineerProfileService — full implementation:
     - updateProfile(), updateLocation(), setOnlineStatus()
     - transitionStatus() (PENDING_DOCUMENTS → UNDER_REVIEW → APPROVED)
     - addQualification(), verifyQualification()
     - addInsurance(), verifyInsurance()
     - setAvailability(), addOverride(), getAvailableSlots()
     - recalculateStats() (stub — will be wired to reviews later)

□ 3. Controllers:
     - PUT  /api/v1/engineer/profile
     - PUT  /api/v1/engineer/location
     - PUT  /api/v1/engineer/online-status
     - POST /api/v1/engineer/qualifications
     - POST /api/v1/engineer/insurance
     - PUT  /api/v1/engineer/availability
     - POST /api/v1/engineer/availability/override
     - PUT  /api/v1/admin/engineers/{id}/approve     (admin)
     - PUT  /api/v1/admin/engineers/{id}/verify-qualification/{qId}

□ 4. Events:
     - EngineerApprovedEvent
     - EngineerWentOnlineEvent
```

**Test Checkpoint:**
```bash
# Register as engineer → status = PENDING_VERIFICATION
# Add Gas Safe qualification → qualification row created
# Add insurance → insurance row created
# Set availability → 6 availability rows (Mon-Sat)
# Admin approves → user.status = ACTIVE, engineer_profile.status = APPROVED
# Go online → is_online = true
# Verify: this engineer now shows up in matching queries
```

---

## Phase 6 — Matching Engine (Week 6–7)

**Goal:** When a job is created, the system automatically finds the best engineer and offers the job.

**Depends on:** Phase 4 (jobs), Phase 5 (engineers with qualifications and availability)

```
Build Checklist:
────────────────────────────────────────────────────────────
□ 1. MatchingService:
     - findCandidates() — PostGIS spatial query + qualification filter
       + availability check + daily job cap check
     - scoreCandidate() — weighted: distance(40%) + rating(25%)
       + acceptance_rate(20%) + tier(15%)
     - offerToTopCandidate() — pick best, create match log, update job
     - handleOfferExpiry() — timeout, try next candidate

□ 2. Native PostGIS query in EngineerProfileRepository:
     @Query(value = """
       SELECT ep.* FROM engineer_profiles ep
       WHERE ep.status = 'APPROVED' AND ep.is_online = true
       AND ST_DWithin(ep.location, ST_MakePoint(:lng, :lat)::geography,
                      ep.service_radius_miles * 1609.34)
       """, nativeQuery = true)
     List<EngineerProfile> findNearbyOnline(double lat, double lng);

□ 3. MatchingJobListener:
     - Wire the real listener (replace the stub from Phase 4)
     - onJobCreated → matchingService.offerToTopCandidate()

□ 4. Offer expiry mechanism:
     - Option A: Redis key with 10-min TTL + key expiry listener
     - Option B: Scheduled task every 30s checks for expired offers
     - Start with Option B (simpler)

□ 5. Scheduled task:
     - processUnmatchedJobs() — CRON every 30s
     - processOfferExpiries() — CRON every 30s

□ 6. Escalation:
     - If no candidates found after N attempts → escalateJob()
```

**Test Checkpoint:**
```bash
# Setup: 1 approved, online engineer with Gas Safe qualification
# Setup: 1 property within the engineer's service radius
# Book a gas safety job
# → Within seconds: job.status = MATCHED, engineer_id populated
# → job_match_log has 1 row with score and distance
# → Engineer receives push notification (or log message for now)

# Test decline: engineer declines → job reverts to CREATED
# → matching re-triggers → offers to next candidate (or escalates)
```

---

## Phase 7 — Certificate + Inspection Data (Week 7–8)

**Goal:** After a job is completed, the engineer submits inspection data and a certificate is generated.

**Depends on:** Phase 4 (jobs in COMPLETED state)

```
Build Checklist:
────────────────────────────────────────────────────────────
□ 1. Repositories:
     - CertificateRepository
     - GasSafetyInspectionRepository
     - GasApplianceInspectionRepository
     - EpcAssessmentRepository
     - EicrInspectionRepository

□ 2. Inspection Services:
     - GasSafetyInspectionService.create() — validate appliances, calculate result
     - EpcAssessmentService.create() — validate score/rating consistency
     - EicrInspectionService.create() — validate defect counts vs result

□ 3. CertificateService:
     - issueCertificate() — create inspection + certificate + PDF (stub PDF gen)
     - Sets certificate_number, expiry_at, document_url (placeholder)
     - Supersedes any old active cert for same property + type
     - Publishes CertificateIssuedEvent

□ 4. Event listeners:
     - On CertificateIssuedEvent → jobService.certifyJob() (COMPLETED → CERTIFIED)
     - On CertificateIssuedEvent → propertyService.updateComplianceStatus()

□ 5. Controllers:
     - POST /api/v1/jobs/{id}/inspection/gas-safety     → submit CP12 data
     - POST /api/v1/jobs/{id}/inspection/epc            → submit EPC data
     - POST /api/v1/jobs/{id}/inspection/eicr           → submit EICR data
     - GET  /api/v1/certificates/{id}                    → certificate detail
     - GET  /api/v1/properties/{id}/certificates         → certs for a property

□ 6. DTOs — the big ones:
     - GasInspectionRequest (includes List<GasApplianceRequest>)
     - EpcAssessmentRequest (building fabric, heating, renewables)
     - EicrInspectionRequest (defect counts, observations)
```

**Test Checkpoint:**
```bash
# Walk a job to COMPLETED state (Phase 4)
# Submit gas safety inspection with 3 appliances
curl -X POST http://localhost:8080/api/v1/jobs/<id>/inspection/gas-safety \
  -d '{
    "overallResult":"PASS",
    "inspectionDate":"2025-06-18",
    "appliances":[
      {"applianceOrder":1,"applianceType":"BOILER","make":"Worcester","model":"Greenstar 30i",
       "result":"PASS","coReadingPercent":0.002,"co2ReadingPercent":9.15},
      ...
    ]
  }'
# → 201
# → Certificate created with status ACTIVE
# → Job status = CERTIFIED (event chain worked!)
# → Property compliance_status updated to {"gas_safety":"VALID",...}
# → Check: old certificate superseded if one existed
```

---

## Phase 8 — Stripe Payments (Week 8–9)

**Goal:** Real money flow. Customer pays on booking, payment is captured on certification, engineer gets paid out.

**Depends on:** Phase 4 (jobs), Phase 7 (certification triggers capture)

```
Build Checklist:
────────────────────────────────────────────────────────────
□ 1. Stripe SDK integration:
     - Add stripe-java dependency
     - StripeConfig — API key from environment variable
     - Use STRIPE TEST MODE keys (sk_test_...)

□ 2. PaymentService:
     - createPaymentIntent() — Stripe API: manual capture mode
     - capturePayment() — Stripe API: capture the held funds
     - refundPayment() — Stripe API: full or partial refund

□ 3. PayoutService:
     - createPayout() — Stripe Transfer to connected account
     - completePayout() — called by webhook when bank confirms

□ 4. StripeWebhookService:
     - handleWebhook() — signature validation + idempotency check
     - Route: payment_intent.succeeded, charge.refunded, payout.paid

□ 5. PaymentJobListener — wire the REAL listener:
     - onJobCertified → capturePayment() + createPayout()
     - onJobCancelled → refundPayment()

□ 6. Stripe Connect (engineer onboarding):
     - Endpoint to generate Stripe Connect onboarding link
     - Webhook handler for account.updated
     - Update engineer_profile.stripe_account_id + stripe_onboarded

□ 7. Controllers:
     - POST /api/v1/webhooks/stripe          → Stripe webhook receiver
     - POST /api/v1/engineer/stripe/onboard  → get Connect onboarding link
     - GET  /api/v1/engineer/earnings        → payout history + summary

□ 8. Flyway migration:
     - V3__stripe_webhook_events.sql (if not already in V1)
```

**Test Checkpoint (with Stripe test mode):**
```bash
# Book a job → payment.status = PENDING, stripe_payment_intent_id populated
# Walk job through to CERTIFIED
# → payment.status = CAPTURED, stripe_charge_id populated
# → payout created with correct commission calculation
# → Stripe dashboard shows the test payment

# Cancel a job after acceptance
# → payment.status = REFUNDED
# → Stripe dashboard shows the test refund

# Test webhook: use Stripe CLI to forward webhooks
stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe
```

---

## Phase 9 — Notifications (Week 9–10)

**Goal:** Push notifications (Firebase), emails (SendGrid), and SMS (Twilio) are sent at the right moments.

**Depends on:** Phase 4 (job events to react to)

```
Build Checklist:
────────────────────────────────────────────────────────────
□ 1. Channel adapters (infrastructure layer):
     - FirebasePushAdapter — wraps Firebase Admin SDK
     - SendGridEmailAdapter — wraps SendGrid API
     - TwilioSmsAdapter — wraps Twilio API
     (Start with just console logging. Wire real APIs one at a time.)

□ 2. NotificationService:
     - send() — creates Notification row + dispatches to adapter
     - markRead(), markAllRead(), getUnreadCount()

□ 3. NotificationJobListener — wire the REAL listener:
     - Replace all stub log.info() with real notificationService.send() calls
     - Handle: JobCreated, Matched, Accepted, EnRoute, Started,
       Certified, Cancelled

□ 4. Device token management:
     - POST /api/v1/notifications/device-token   → register FCM token
     - Store on user or in a separate table
     (Firebase needs the device token to send push notifications)

□ 5. Controllers:
     - GET  /api/v1/notifications                 → my notifications (paginated)
     - PUT  /api/v1/notifications/{id}/read       → mark as read
     - PUT  /api/v1/notifications/read-all        → mark all read
     - GET  /api/v1/notifications/unread-count     → badge count

□ 6. SendGrid email templates:
     - Welcome email
     - Job confirmation
     - Certificate ready
     - Payment receipt
     - Renewal reminder
```

**Test Checkpoint:**
```bash
# Book a job → notification row created with channel=PUSH, status=SENT
# Walk through full lifecycle → multiple notification rows
# GET /api/v1/notifications → see all notifications in order
# GET /api/v1/notifications/unread-count → correct count
# Mark as read → count decrements
# Check: Firebase console shows test push (or console log output)
```

---

## Phase 10 — Reviews + Messaging + Documents (Week 10–11)

**Goal:** Post-job reviews, in-job messaging, and file uploads work.

**Depends on:** Phase 4 (jobs), Phase 7 (CERTIFIED jobs for reviews)

```
Build Checklist:
────────────────────────────────────────────────────────────
□ 1. ReviewService:
     - submitReview() — validate direction, rating, one review per direction per job
     - Publish ReviewSubmittedEvent → recalculate engineer stats

□ 2. MessageService:
     - sendMessage(), sendSystemMessage(), markRead()
     - Messages scoped to job

□ 3. DocumentService:
     - upload() to S3 (use LocalStack or MinIO for dev)
     - generatePresignedDownloadUrl()
     - Virus scan stub (log only)

□ 4. Wire UserStatsJobListener:
     - On ReviewSubmittedEvent → engineerProfileService.recalculateStats()

□ 5. Controllers:
     - POST /api/v1/jobs/{id}/reviews             → submit review
     - GET  /api/v1/jobs/{id}/reviews             → reviews for a job
     - GET  /api/v1/users/{id}/reviews            → reviews for a user
     - POST /api/v1/jobs/{id}/messages            → send message
     - GET  /api/v1/jobs/{id}/messages            → message history
     - POST /api/v1/documents/upload              → upload file
     - GET  /api/v1/documents/{id}/download       → presigned URL
```

---

## Phase 11 — Compliance + Renewals + GDPR (Week 11–12)

**Goal:** Renewal reminders fire on schedule. Customers can request data export. Compliance dashboard works.

**Depends on:** Phase 7 (certificates with expiry dates), Phase 9 (notifications)

```
Build Checklist:
────────────────────────────────────────────────────────────
□ 1. RenewalReminderService:
     - scheduleReminders() — create 6 rows on cert issuance
     - processDueReminders() — CRON daily at 8am
     - cancelReminders() — when cert is superseded

□ 2. CertificateService additions:
     - markExpired() — CRON daily, finds expired certs
     - generateShareToken() — public share link

□ 3. DataRequestService:
     - createExportRequest(), processExportRequest()
     - Collect from all tables, generate ZIP, upload to S3

□ 4. ComplianceDashboard:
     - GET /api/v1/compliance/dashboard
     - Aggregates: total properties, valid certs, expiring, expired, missing

□ 5. Scheduled tasks (wire remaining CRONs):
     - processDueReminders() — daily 8am
     - expireCertificates() — daily 8am
     - cleanupExpiredRefreshTokens() — daily 2am
     - flagExpiringQualifications() — daily 8am
     - recalculateAllEngineerStats() — weekly Sunday 3am

□ 6. Controllers:
     - GET  /api/v1/compliance/dashboard
     - GET  /api/v1/certificates/shared/{token}   → public cert view
     - POST /api/v1/gdpr/export                   → request data export
     - GET  /api/v1/gdpr/requests                 → my GDPR requests
```

---

## Phase 12 — Admin + Feature Flags + Polish (Week 12–13)

**Goal:** Admin panel endpoints. Feature flags control rollout. Audit trail is complete.

**Depends on:** Everything

```
Build Checklist:
────────────────────────────────────────────────────────────
□ 1. FeatureFlagService:
     - isEnabled(), isEnabledForUser() — percentage rollout
     - CRUD for admin

□ 2. AuditLogService:
     - log(), search(), getByEntity()
     - Wire AuditEventListener to catch all domain events

□ 3. Admin endpoints:
     - GET  /api/v1/admin/users                   → user management
     - PUT  /api/v1/admin/users/{id}/suspend
     - GET  /api/v1/admin/jobs                    → all jobs
     - GET  /api/v1/admin/audit-log               → searchable audit trail
     - POST /api/v1/admin/feature-flags
     - PUT  /api/v1/admin/feature-flags/{key}/toggle
     - GET  /api/v1/admin/dashboard               → system stats

□ 4. Role-based access control:
     - @PreAuthorize("hasRole('ADMIN')") on admin endpoints
     - @PreAuthorize("hasRole('ENGINEER')") on engineer endpoints

□ 5. Performance + polish:
     - API rate limiting (Bucket4j or Spring Cloud Gateway)
     - Response compression
     - Request logging + correlation IDs
     - Swagger/OpenAPI docs (@Operation annotations)
     - Health checks (/actuator/health)
```

---

## Dependency Graph (Visual)

```
                    Phase 0 (Skeleton)
                         │
                    Phase 1 (Auth)
                    ┌────┼────┐
              Phase 2    │    │
            (Profiles)   │    │
                │   Phase 3   │
                │  (Pricing)  │
                └────┬────────┘
                     │
                Phase 4 ⭐
               (Job Core)
              ┌──┬───┼───┬──┐
              │  │   │   │  │
           Ph 5  │ Ph 7  │ Ph 9
         (Engr) │(Cert) │(Notif)
              │  │   │   │
              Ph 6   Ph 8│
           (Match)(Pay)  │
              │  │   │   │
              └──┴───┼───┘
                     │
                  Ph 10
            (Review/Msg/Doc)
                     │
                  Ph 11
              (Compliance)
                     │
                  Ph 12
                 (Admin)
```

---

## Daily Workflow

For each phase, follow this daily pattern:

```
Morning:
  1. Pick the next unchecked item from the phase's build checklist
  2. Write the repository interface (2 min)
  3. Write the DTO records (5 min)
  4. Write the service method (30–60 min)

Afternoon:
  5. Write the controller endpoint (10 min)
  6. Test with curl / Postman (15 min)
  7. Check database state matches E2E scenario doc (10 min)
  8. Commit with message: "Phase X: [what you built]"

End of day:
  9. Run the full happy-path test for the phase
  10. Check off the item on the build checklist
```

---

## What You Already Have (Don't Rebuild)

| Asset | Location | Use It For |
|-------|----------|------------|
| DDL | `certifynow_ddl.sql` | Phase 0 — copy as `V1__initial_schema.sql` |
| JPA Entities | `certifynow-jpa-entities.zip` | Phase 0 — copy all into project |
| Service Plan | `certifynow_service_layer_plan.md` | Every phase — method signatures, caching, events |
| E2E Scenarios | `certifynow_e2e_scenarios.md` | Testing — verify DB state at each step |
| Pattern Reference | `certifynow-service-pattern.zip` | Phase 4 — copy JobService pattern exactly |
| Pattern Guide | `PATTERN.md` | Every phase — the 8 patterns to follow |

---

## Quick Wins If You're Feeling Stuck

If a phase feels too big, do these in isolation:

1. **Just write the repository interface.** It's 10 lines. Now the data access is done.
2. **Just write one service method.** `createJob()` alone is a meaningful deliverable.
3. **Just make the event + a logging listener.** Prove the event bus works before wiring real logic.
4. **Just test with curl.** Don't build a frontend. The API is the product until the backend works.
5. **Skip optional fields.** Get the happy path working with required fields only. Add optional stuff in a polish pass.

---

*You've designed the database, the entities, the services, the events, and the patterns. Now it's just typing. One file at a time.*
