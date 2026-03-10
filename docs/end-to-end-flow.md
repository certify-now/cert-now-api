# CertNow API — End-to-End Gas Safety Flow

This document walks through the complete lifecycle of a Gas Safety (CP12) certificate from a
fresh Spring Boot start to a generated PDF, using only `curl`.

**Base URL:** `http://localhost:8080`  
**Profile used:** `dev`  
**Date run:** 2026-03-09

---

## Prerequisites

1. **Docker running** — PostgreSQL 16 must be up:
   ```bash
   docker compose up -d
   ```

2. **Start Spring Boot** in the `dev` profile:
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=dev'
   ```
   Expected: `Started CertifyNowApplication in ~5s`

---

## Step 1 — Register a Customer

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "customer@certnow.co.uk",
    "password": "Customer123!",
    "full_name": "Jane Smith",
    "phone": "+447700900002",
    "role": "CUSTOMER"
  }'
```

**Response (201):**
```json
{
  "data": {
    "access_token": "<JWT>",
    "refresh_token": "<token>",
    "user": {
      "id": "2a41f2c8-4b99-44d2-bfbb-f3f6d025feab",
      "email": "customer@certnow.co.uk",
      "full_name": "Jane Smith",
      "role": "CUSTOMER",
      "status": "PENDING_VERIFICATION"
    }
  }
}
```

> **Note:** In dev, skip email verification by updating the DB directly:
> ```sql
> UPDATE "user" SET status = 'ACTIVE', email_verified = true WHERE email = 'customer@certnow.co.uk';
> ```

---

## Step 2 — Register an Engineer

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "engineer@certnow.co.uk",
    "password": "Engineer123!",
    "full_name": "John Engineer",
    "phone": "+447700900003",
    "role": "ENGINEER"
  }'
```

**Response (201):**
```json
{
  "data": {
    "access_token": "<JWT>",
    "user": {
      "id": "ae0dbbda-d3a8-4f50-94c8-8020f91cc883",
      "email": "engineer@certnow.co.uk",
      "role": "ENGINEER",
      "status": "PENDING_VERIFICATION",
      "profile": {
        "status": "APPLICATION_SUBMITTED"
      }
    }
  }
}
```

> Activate the engineer account via DB:
> ```sql
> UPDATE "user" SET status = 'ACTIVE', email_verified = true WHERE email = 'engineer@certnow.co.uk';
> ```

---

## Step 3 — Create an Admin User

Admin accounts cannot be self-registered. Insert one directly:

```bash
# 1. Generate a BCrypt hash for your chosen password
CRYPTO_JAR=$(find ~/.gradle -name "spring-security-crypto-6*.jar" | grep -v sources | head -1)
COMMONS_JAR=$(find ~/.gradle -name "commons-logging-*.jar" | grep -v sources | head -1)

cat > /tmp/HashPassword.java << 'EOF'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class HashPassword {
    public static void main(String[] args) {
        System.out.println(new BCryptPasswordEncoder().encode("Admin123!"));
    }
}
EOF
cd /tmp && javac -cp "$CRYPTO_JAR" HashPassword.java && java -cp ".:$CRYPTO_JAR:$COMMONS_JAR" HashPassword
```

```sql
-- 2. Insert the admin record
INSERT INTO "user" (id, email, password_hash, full_name, phone, role, status,
                    email_verified, phone_verified, auth_provider,
                    created_at, date_created, updated_at, last_updated)
VALUES (
  gen_random_uuid(),
  'admin@certnow.co.uk',
  '$2a$10$xB6JLr.PcxnFly2HM/Q/8eny20SE/LMM78iaCTUgkyJqnXYPHOZBa',  -- Admin123!
  'Admin User', '+447700900001', 'ADMIN', 'ACTIVE',
  true, false, 'EMAIL', NOW(), NOW(), NOW(), NOW()
);
```

---

## Step 4 — Login All Three Users

Save the tokens as environment variables for the remaining steps.

```bash
# Admin
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@certnow.co.uk","password":"Admin123!"}' \
  | jq -r '.data.access_token')

# Customer
CUSTOMER_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"customer@certnow.co.uk","password":"Customer123!"}' \
  | jq -r '.data.access_token')

CUSTOMER_ID="2a41f2c8-4b99-44d2-bfbb-f3f6d025feab"

# Engineer
ENGINEER_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"engineer@certnow.co.uk","password":"Engineer123!"}' \
  | jq -r '.data.access_token')

ENGINEER_ID="ae0dbbda-d3a8-4f50-94c8-8020f91cc883"
```

---

## Step 5 — Create a Pricing Rule (Admin)

A national (no region) pricing rule must exist before a job can be booked.

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/pricing/rules \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "certificate_type": "GAS_SAFETY",
    "base_price_pence": 9900,
    "effective_from": "2026-03-09"
  }'
```

**Response (201):**
```json
{
  "data": {
    "id": "3b82de09-cecb-47b7-a638-65d324d7a7f7",
    "certificate_type": "GAS_SAFETY",
    "region": null,
    "base_price_pence": 9900,
    "is_active": true,
    "effective_from": "2026-03-09",
    "modifiers": []
  }
}
```

```bash
PRICING_RULE_ID="3b82de09-cecb-47b7-a638-65d324d7a7f7"
```

---

## Step 6 — Approve the Engineer (Admin)

The engineer must pass through the onboarding state machine before they can go online.

```bash
# Get the engineer's profile ID
ENG_PROFILE_ID=$(curl -s http://localhost:8080/api/v1/engineer/profile \
  -H "Authorization: Bearer $ENGINEER_TOKEN" | jq -r '.data.id')
# → 45eb3551-e22a-4357-a8df-a48f00ca5e79

# Transition through each required state
curl -s -X PUT http://localhost:8080/api/v1/admin/engineers/$ENG_PROFILE_ID/transition-status \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"target_status": "ID_VERIFICATION_PENDING"}' | jq '.data.status'
# → "ID_VERIFICATION_PENDING"

curl -s -X PUT http://localhost:8080/api/v1/admin/engineers/$ENG_PROFILE_ID/transition-status \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"target_status": "DBS_CHECK_PENDING"}' | jq '.data.status'
# → "DBS_CHECK_PENDING"

curl -s -X PUT http://localhost:8080/api/v1/admin/engineers/$ENG_PROFILE_ID/transition-status \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"target_status": "INSURANCE_VERIFICATION_PENDING"}' | jq '.data.status'
# → "INSURANCE_VERIFICATION_PENDING"

curl -s -X PUT http://localhost:8080/api/v1/admin/engineers/$ENG_PROFILE_ID/approve \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '{status: .data.status, approved_at: .data.approved_at}'
# → { "status": "APPROVED", "approved_at": "2026-03-09T16:04:06Z" }
```

**Engineer approval state machine:**
```
APPLICATION_SUBMITTED → ID_VERIFICATION_PENDING → DBS_CHECK_PENDING
  → INSURANCE_VERIFICATION_PENDING → APPROVED
```

---

## Step 7 — Engineer Goes Online

```bash
# Set GPS location
curl -s -X PUT http://localhost:8080/api/v1/engineer/location \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ENGINEER_TOKEN" \
  -d '{"latitude": 51.5074, "longitude": -0.1278}'

# Go online
curl -s -X PUT http://localhost:8080/api/v1/engineer/online-status \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ENGINEER_TOKEN" \
  -d '{"is_online": true}'
```

---

## Step 8 — Customer Creates a Property

```bash
curl -s -X POST http://localhost:8080/api/v1/properties \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -d '{
    "address_line1": "123 Gas Street",
    "address_line2": "Flat 2",
    "city": "London",
    "county": "Greater London",
    "postcode": "SW1A 1AA",
    "country": "UK",
    "property_type": "FLAT",
    "bedrooms": 2,
    "year_built": 1990,
    "has_gas_supply": true,
    "has_electric": true
  }'
```

**Response (201):**
```json
{
  "data": {
    "id": "8f22af4e-841f-43a7-96e1-414d4e9ab790",
    "address_line1": "123 Gas Street",
    "address_line2": "Flat 2",
    "city": "London",
    "postcode": "SW1A 1AA",
    "property_type": "FLAT",
    "bedrooms": 2,
    "has_gas_supply": true,
    "has_electric": true,
    "compliance_status": "PENDING",
    "is_active": true
  }
}
```

```bash
PROPERTY_ID="8f22af4e-841f-43a7-96e1-414d4e9ab790"
```

---

## Step 9 — Customer Creates a Job

```bash
curl -s -X POST http://localhost:8080/api/v1/jobs \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -d "{
    \"property_id\": \"$PROPERTY_ID\",
    \"certificate_type\": \"GAS_SAFETY\",
    \"urgency\": \"STANDARD\",
    \"access_instructions\": \"Ring doorbell, code 1234\",
    \"customer_notes\": \"Annual boiler service required\"
  }"
```

**Response (201):**
```json
{
  "data": {
    "id": "8d2a4f1c-...",
    "reference_number": "CN-20260309-77Y3",
    "status": "CREATED",
    "certificate_type": "GAS_SAFETY",
    "urgency": "STANDARD",
    "pricing": {
      "base_price_pence": 9900,
      "total_price_pence": 9900,
      "commission_rate": 0.15,
      "commission_pence": 1485,
      "engineer_payout_pence": 8415
    },
    "payment": {
      "status": "PENDING",
      "amount_pence": 9900
    }
  }
}
```

```bash
JOB_ID="<id from response>"
```

---

## Step 10 — Admin Matches Job to Engineer

> In production this is done automatically by the matching engine.
> This manual endpoint (`PUT /match`) is provided for dev/testing.

```bash
curl -s -X PUT http://localhost:8080/api/v1/jobs/$JOB_ID/match \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"engineer_id\": \"$ENGINEER_ID\"}"
```

**Response:** `status: "MATCHED"`

---

## Step 11 — Engineer Accepts the Job

```bash
curl -s -X PUT http://localhost:8080/api/v1/jobs/$JOB_ID/accept \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ENGINEER_TOKEN" \
  -d '{
    "scheduled_date": "2026-03-15",
    "scheduled_time_slot": "MORNING"
  }'
```

**Response:** `status: "ACCEPTED"`

> `scheduled_date` must be **today or within 14 days** (not further out). Time slots: `MORNING`, `AFTERNOON`, `EVENING`.

---

## Step 12 — Engineer Marks En Route

```bash
curl -s -X PUT http://localhost:8080/api/v1/jobs/$JOB_ID/en-route \
  -H "Authorization: Bearer $ENGINEER_TOKEN"
```

**Response:** `status: "EN_ROUTE"`

---

## Step 13 — Engineer Starts Job (On-Site Check-In)

```bash
curl -s -X PUT http://localhost:8080/api/v1/jobs/$JOB_ID/start \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ENGINEER_TOKEN" \
  -d '{"latitude": 51.4976, "longitude": -0.1357}'
```

**Response:** `status: "IN_PROGRESS"`

---

## Step 14 — Engineer Completes the Job

```bash
curl -s -X PUT http://localhost:8080/api/v1/jobs/$JOB_ID/complete \
  -H "Authorization: Bearer $ENGINEER_TOKEN"
```

**Response:** `status: "COMPLETED"`

---

## Step 15 — Engineer Submits Gas Safety Record

This is the critical step. The engineer submits all inspection data. On success:

1. A `Certificate` record is created with `status: ACTIVE`
2. The job transitions to `CERTIFIED`
3. A `CertificateIssuedEvent` fires **after transaction commit**
4. Asynchronously, the PDF is generated and stored

```bash
curl -s -X POST http://localhost:8080/api/v1/jobs/$JOB_ID/inspection/gas-safety \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ENGINEER_TOKEN" \
  -d '{
    "certificate": {
      "certificate_number": "GS-2026-00125",
      "certificate_reference": "REF-003",
      "certificate_type": "GAS_SAFETY",
      "issue_date": "2026-03-09",
      "next_inspection_due_on_or_before": "2027-03-09",
      "number_of_appliances_tested": 1
    },
    "company_details": {
      "trading_title": "CertNow Ltd",
      "address_line1": "1 Tech Hub, Shoreditch",
      "address_line2": "London",
      "post_code": "EC1A1BB",
      "gas_safe_registration_number": "123456",
      "company_phone": "+447700900099"
    },
    "engineer_details": {
      "name": "John Engineer",
      "gas_safe_registration_number": "E789012",
      "engineer_licence_card_number": "LIC001",
      "time_of_arrival": "09:00",
      "time_of_departure": "11:00",
      "report_issued_date": "2026-03-09"
    },
    "client_details": {
      "name": "Jane Smith",
      "address_line1": "123 Gas Street",
      "address_line2": "Flat 2",
      "post_code": "SW1A1AA",
      "telephone": "07700900002",
      "email": "customer@certnow.co.uk"
    },
    "tenant_details": null,
    "installation_details": {
      "name_or_flat": "Flat 2",
      "address_line1": "123 Gas Street",
      "post_code": "SW1A1AA"
    },
    "appliances": [
      {
        "index": 1,
        "location": "Kitchen",
        "appliance_type": "Boiler",
        "make": "Baxi",
        "model": "100 Combi",
        "serial_number": "BX-2019-44521",
        "landlords_appliance": true,
        "inspection_type": "ANNUAL",
        "appliance_inspected": true,
        "appliance_safe_to_use": true,
        "ventilation_provision_satisfactory": true,
        "flue_visual_condition_termination_satisfactory": true,
        "flue_performance_tests": "PASS",
        "spillage_test": "PASS",
        "operating_pressure_mbar": 20.0,
        "burner_pressure_mbar": 18.5,
        "gas_rate": "Normal",
        "heat_input_kw": 24.0,
        "safety_devices_correct_operation": true,
        "emergency_control_accessible": true,
        "gas_installation_pipework_visual_inspection_satisfactory": true,
        "gas_tightness_satisfactory": true,
        "equipotential_bonding": true,
        "warning_notice_fixed": false,
        "additional_notes": "All checks satisfactory."
      }
    ],
    "final_checks": {
      "gas_tightness_pass": "PASS",
      "gas_pipe_work_visual_pass": "PASS",
      "emergency_control_accessible": "YES",
      "equipotential_bonding": "PASS",
      "installation_pass": "PASS",
      "co_alarm_fitted_working_same_room": "YES",
      "smoke_alarm_fitted_working": "YES",
      "additional_observations": "All checks satisfactory."
    },
    "faults_and_remedials": {
      "faults_found": false,
      "remedial_action_taken": false
    },
    "signatures": {
      "engineer_signed": true,
      "engineer_signed_date": "2026-03-09",
      "customer_name": "Jane Smith",
      "customer_signed": true,
      "customer_signed_date": "2026-03-09",
      "privacy_policy_accepted": true
    },
    "metadata": {
      "created_by_software": "CertNow API",
      "version": "1.0",
      "platform": "API"
    }
  }'
```

**Response (201):**
```json
{
  "data": {
    "id": "551262f9-bfcd-478b-a9ef-0b2dc5f5dfc3",
    "job_id": "...",
    "certificate_number": "GS-2026-00125",
    "issue_date": "2026-03-09",
    "next_inspection_due_on_or_before": "2027-03-09",
    "qr_code_url": null,
    "verification_url": null,
    "appliances": [...],
    "final_checks": {...},
    "signatures": {...}
  }
}
```

> `qr_code_url` and `verification_url` are `null` immediately — they are populated
> asynchronously within ~2 seconds once the PDF is generated.

---

## Step 16 — PDF Generated Asynchronously

After the inspection is submitted, the following happens on a background thread (`pdf-gen-1`):

```
CertificateIssuedEvent (AFTER_COMMIT)
  → CertificateIssuedEventListener.onCertificateIssued()
  → GasSafetyCertificatePdfService.generateAndStore(certificateId)
      1. Load Certificate + GasSafetyRecord (with appliances eagerly fetched)
      2. Generate QR code PNG (ZXing) for verification URL
      3. Map domain → GasSafetyCertificatePdfModel
      4. Render Thymeleaf HTML template (gas-safety-certificate.html)
      5. Convert HTML → PDF bytes (OpenHTMLtoPDF + PDFBox)
      6. Store PDF via StubDocumentStorageService → /tmp/cert-now-pdfs/{id}.pdf
      7. Update Certificate.documentUrl + GasSafetyRecord.qrCodeUrl/verificationUrl
```

**Poll until the PDF URL is populated (~2s):**

```bash
# Poll until qr_code_url is populated
curl -s http://localhost:8080/api/v1/jobs/$JOB_ID/inspection/gas-safety \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  | jq '{qr_code_url: .data.qr_code_url, verification_url: .data.verification_url}'
```

**Response once PDF is ready:**
```json
{
  "qr_code_url": "file:///var/folders/.../cert-now-pdfs/f86b6fc4-18c0-4b8a-837a-534cf3769c35.pdf",
  "verification_url": "http://localhost:8080/certificates/f86b6fc4-18c0-4b8a-837a-534cf3769c35"
}
```

---

## Step 17 — View the PDF

Extract the file path and open the PDF locally:

```bash
PDF_PATH=$(curl -s http://localhost:8080/api/v1/jobs/$JOB_ID/inspection/gas-safety \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  | jq -r '.data.qr_code_url' | sed 's|file://||')

open "$PDF_PATH"   # macOS
# or: xdg-open "$PDF_PATH"   # Linux
```

The generated PDF is an A4 portrait **CP12 / Landlord Gas Safety Certificate** containing:
- Company logo section + Gas Safe registration number
- Engineer details + arrival/departure times
- Client + installation address
- Per-appliance inspection results (combustion readings, classification codes)
- Final checks grid (PASS/FAIL)
- Fault notes and remedial actions
- Signatures section
- QR code linking to the verification URL

---

## Step 18 — Verify Final Job State

```bash
curl -s http://localhost:8080/api/v1/jobs/$JOB_ID \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  | jq '{ref: .data.reference_number, status: .data.status, certified_at: .data.timestamps.certified_at}'
```

**Response:**
```json
{
  "ref": "CN-20260309-77Y3",
  "status": "CERTIFIED",
  "certified_at": "2026-03-09T16:17:41.938875Z"
}
```

---

## Job Status State Machine

```
CREATED
  → MATCHED        (admin assigns an engineer)
  → ACCEPTED       (engineer accepts + schedules date/slot)
  → EN_ROUTE       (engineer departs for property)
  → IN_PROGRESS    (engineer arrives on-site with GPS check-in)
  → COMPLETED      (engineer marks work done)
  → CERTIFIED      (engineer submits inspection record → PDF generated)
```

Terminal states: `CERTIFIED`, `CANCELLED`, `FAILED`

---

## Full API Reference (used in this flow)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/auth/register` | None | Register customer or engineer |
| `POST` | `/api/v1/auth/login` | None | Login and get JWT tokens |
| `POST` | `/api/v1/auth/refresh` | None | Refresh access token |
| `POST` | `/api/v1/auth/logout` | Bearer | Revoke tokens |
| `GET` | `/api/v1/users/me` | Bearer | Get current user profile |
| `POST` | `/api/v1/admin/pricing/rules` | Admin | Create pricing rule |
| `GET` | `/api/v1/admin/pricing/rules` | Admin | List pricing rules |
| `PUT` | `/api/v1/admin/pricing/urgency-multipliers/{id}` | Admin | Update urgency multiplier |
| `GET` | `/api/v1/admin/engineers` | Admin | List all engineers |
| `PUT` | `/api/v1/admin/engineers/{id}/approve` | Admin | Approve engineer |
| `PUT` | `/api/v1/admin/engineers/{id}/transition-status` | Admin | Transition engineer status |
| `POST` | `/api/v1/properties` | Customer | Create property |
| `GET` | `/api/v1/properties` | Customer | List my properties |
| `GET` | `/api/v1/engineer/profile` | Engineer | Get engineer profile |
| `PUT` | `/api/v1/engineer/profile` | Engineer | Update engineer profile |
| `PUT` | `/api/v1/engineer/location` | Engineer | Update GPS location |
| `PUT` | `/api/v1/engineer/online-status` | Engineer | Toggle online status |
| `POST` | `/api/v1/engineer/qualifications` | Engineer | Add qualification |
| `POST` | `/api/v1/engineer/insurance` | Engineer | Add insurance |
| `POST` | `/api/v1/jobs` | Customer | Create a job |
| `GET` | `/api/v1/jobs` | Any | List jobs (role-filtered) |
| `GET` | `/api/v1/jobs/{id}` | Any | Get job details |
| `PUT` | `/api/v1/jobs/{id}/match` | Admin | Manually match engineer (dev only) |
| `PUT` | `/api/v1/jobs/{id}/accept` | Engineer | Accept job + schedule |
| `PUT` | `/api/v1/jobs/{id}/decline` | Engineer | Decline job |
| `PUT` | `/api/v1/jobs/{id}/en-route` | Engineer | Mark en route |
| `PUT` | `/api/v1/jobs/{id}/start` | Engineer | Start job (GPS check-in) |
| `PUT` | `/api/v1/jobs/{id}/complete` | Engineer | Complete job |
| `PUT` | `/api/v1/jobs/{id}/cancel` | Any | Cancel job |
| `GET` | `/api/v1/jobs/{id}/history` | Any | Get status history |
| `POST` | `/api/v1/jobs/{id}/inspection/gas-safety` | Engineer | Submit gas safety record |
| `GET` | `/api/v1/jobs/{id}/inspection/gas-safety` | Any | Get gas safety record |

---

## Known Field Constraints

When submitting a gas safety record, these DB columns are `varchar(10)` — keep values ≤ 10 chars:

| Field | Acceptable values |
|-------|-------------------|
| `final_checks.gas_tightness_pass` | `"PASS"`, `"FAIL"` |
| `final_checks.gas_pipe_work_visual_pass` | `"PASS"`, `"FAIL"` |
| `final_checks.emergency_control_accessible` | `"YES"`, `"NO"` |
| `final_checks.equipotential_bonding` | `"PASS"`, `"FAIL"` |
| `final_checks.installation_pass` | `"PASS"`, `"FAIL"` |
| `final_checks.co_alarm_fitted_working_same_room` | `"YES"`, `"NO"` |
| `final_checks.smoke_alarm_fitted_working` | `"YES"`, `"NO"` |

---

## Bugs Fixed During This Run

Three pre-existing bugs were identified and fixed while running this flow:

| # | File | Bug | Fix |
|---|------|-----|-----|
| 1 | `GasSafetyRecordRepository` | `findByJobId` did not eagerly fetch `appliances`, causing `LazyInitializationException` in async PDF thread | Added `findByJobIdWithAppliances` with `LEFT JOIN FETCH r.appliances` |
| 2 | `JobService` | `getById` and `listJobs` lacked `@Transactional`, causing `LazyInitializationException` accessing `job.getProperty()` | Added `@Transactional(readOnly = true)` to both methods |
| 3 | `GasSafetyCertificatePdfService` | `attemptGenerateAndStore` was called via `this.` (self-invocation), bypassing the Spring AOP proxy and making `@Transactional` a no-op — so `document_url` was never persisted | Added `@Lazy @Autowired self` reference; call `self.attemptGenerateAndStore()` via the proxy |

---

## Useful DB Queries

```sql
-- Check all users
SELECT id, email, role, status FROM "user";

-- Check job lifecycle
SELECT id, reference_number, status, certified_at FROM job ORDER BY created_at DESC;

-- Check certificate + PDF URL
SELECT j.reference_number, c.id, c.document_url
FROM certificate c JOIN job j ON j.id = c.job_id;

-- Check gas safety record URLs
SELECT g.certificate_number, g.qr_code_url, g.verification_url
FROM gas_safety_record g;

-- Check pricing rules
SELECT certificate_type, region, base_price_pence, is_active FROM pricing_rule;
```

---

## Swagger UI

Available at: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

All endpoints are documented. Use the **Authorize** button (top right) and enter:
```
Bearer <your_access_token>
```
