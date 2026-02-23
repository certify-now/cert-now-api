# CertifyNow — Authentication & Registration Flow

## 1. High-Level Architecture

```
HTTP Request
    │
    ▼
AuthController          ← REST layer: validates input, maps HTTP → facade calls
    │
    ▼
AuthFacade              ← Orchestration only, zero business logic
    ├── RegistrationService    ← user creation, duplicate detection, consent recording
    ├── AuthenticationService  ← credential validation, account status checks
    ├── SessionService         ← token issuance, rotation, revocation
    ├── EmailVerificationService ← token generation, email sending, token verification
    └── AuthMapper             ← entity → DTO mapping
```

All request/response bodies use `snake_case` JSON keys (e.g. `full_name`, `refresh_token`, `access_token`).

---

## 2. API Reference

### 2.1 `POST /api/v1/auth/register`

**What it does:** Creates a new user account, records consent, and returns a token pair so the user is immediately signed in after registration.

**When to use it:** Call this once when a user fills out the sign-up form for the first time. It handles both `CUSTOMER` and `ENGINEER` roles in a single endpoint.

#### Request

```json
{
  "email":     "jane@example.com",         // required — must be a valid email
  "password":  "Password1!",               // required — ≥8 chars, upper, lower, digit, special
  "full_name": "Jane Smith",               // required — 2–100 characters
  "phone":     "+447911123456",            // optional — must be a valid UK number (+44XXXXXXXXXX)
  "role":      "CUSTOMER" | "ENGINEER"     // required
}
```

#### Response — `201 Created`

```json
{
  "data": {
    "access_token":  "<JWT>",
    "refresh_token": "<opaque hex>",
    "token_type":    "Bearer",
    "expires_in":    900,
    "user": {
      "id":             "<UUID>",
      "email":          "jane@example.com",
      "full_name":      "Jane Smith",
      "role":           "CUSTOMER",
      "status":         "ACTIVE",
      "email_verified": false,
      "profile":        { ... }
    }
  },
  "request_id": "<UUID>"
}
```

> **Silent duplicate:** If the email or phone is already registered, the response looks identical to a real success (same `201`, same shape). No 409 is returned — this prevents email enumeration. The real account owner receives a security notification email instead.

#### What happens in the service layer

```
AuthController.register()
  │  Extracts client IP from X-Forwarded-For / remoteAddr (IpAddressUtils)
  │
  └─ AuthFacade.register(request, ipAddress)
       │
       └─ RegistrationService.registerUser()   [@Transactional]
            │
            ├─ 1. Duplicate email check (case-insensitive)
            │       Duplicate? → publish DuplicateRegistrationAttemptEvent (async)
            │                    return Optional.empty()
            │
            ├─ 2. Duplicate phone check (only if phone is provided and non-blank)
            │       Duplicate? → publish DuplicateRegistrationAttemptEvent (async)
            │                    return Optional.empty()
            │
            ├─ 3. UserFactory.createEmailUser()
            │       - BCrypt-hashes the password
            │       - Sets role and auth provider (EMAIL)
            │       - CUSTOMER → status ACTIVE
            │         ENGINEER → status PENDING_VERIFICATION
            │       - Sets emailVerified = false
            │
            ├─ 4. userRepository.save(user)
            │
            ├─ 5. ProfileFactory → create and save role-specific profile
            │       CUSTOMER → CustomerProfile (compliance score, letting agent flag…)
            │       ENGINEER → EngineerProfile (tier, bio, service radius…)
            │       ADMIN    → no profile created
            │
            ├─ 6. Create UserConsent records (TERMS_OF_SERVICE + PRIVACY_POLICY)
            │       IP address stored on each record for audit trail
            │
            └─ 7. Publish UserRegisteredEvent
                    ↓  [TRANSACTION COMMITS HERE]
                    ↓
            EmailVerificationEventListener   [AFTER_COMMIT]
                    └─ EmailVerificationService.sendVerificationEmail()
                            - Deletes old unused tokens for this user
                            - Generates 64-char hex token (SecureRandom)
                            - SHA-256 hashes it before storing in DB
                            - Sends email: {frontend}/verify-email?token=<rawToken>

       └─ If user was created: SessionService.issueTokens()
               - JwtTokenProvider.generateAccessToken(user) → signed JWT (HS512, 15 min)
               - RefreshTokenService.issueToken() → new token family started
                   - SHA-256 hash stored in DB, raw token returned to client
               → TokenPair (access + refresh)

       └─ AuthMapper.toAuthResponse() → builds the AuthResponse DTO
```

> **Why AFTER_COMMIT for verification email?** If the SMTP call were inside the transaction and failed, the entire user record would roll back. Listening after commit means the user is always saved, even if the email provider is temporarily down.

---

### 2.2 `POST /api/v1/auth/login`

**What it does:** Validates email/password credentials and returns a fresh token pair.

**When to use it:** Every time a user enters their credentials on the login screen. Also called after a session expires and `refresh` is not possible (e.g. refresh token expired after 30 days).

#### Request

```json
{
  "email":       "jane@example.com",  // required
  "password":    "Password1!",        // required
  "device_info": "iPhone 15 / iOS 17" // optional — stored for audit trail
}
```

#### Response — `200 OK`

```json
{
  "data": {
    "access_token":  "<JWT>",
    "refresh_token": "<opaque hex>",
    "token_type":    "Bearer",
    "expires_in":    900,
    "user": { ... }
  },
  "request_id": "<UUID>"
}
```

#### Error cases

| Condition | HTTP | Code |
|---|---|---|
| Email not found | 401 | `INVALID_CREDENTIALS` |
| Wrong password | 401 | `INVALID_CREDENTIALS` |
| Account deactivated | 401 | `ACCOUNT_DEACTIVATED` |
| Account suspended | 403 | `ACCOUNT_SUSPENDED` |

> Both "email not found" and "wrong password" return the same `INVALID_CREDENTIALS` error — this prevents username enumeration.

#### What happens in the service layer

```
AuthController.login()
  │  Extracts client IP
  │
  └─ AuthFacade.login(request, ipAddress)
       │
       └─ AuthenticationService.authenticate()   [@Transactional]
            │
            ├─ 1. userRepository.findByEmailIgnoreCase(email)
            │       Not found → 401 INVALID_CREDENTIALS
            │
            ├─ 2. passwordEncoder.matches(password, user.passwordHash)
            │       No match → 401 INVALID_CREDENTIALS
            │
            ├─ 3. validateAccountStatus(user)
            │       DEACTIVATED → 401 ACCOUNT_DEACTIVATED
            │       SUSPENDED   → 403 ACCOUNT_SUSPENDED
            │       PENDING_VERIFICATION → allowed (gated later by @RequiresVerifiedEmail)
            │
            ├─ 4. user.updateLastLogin(clock)
            │       Updates lastLoginAt timestamp on the user entity
            │
            └─ 5. Publish UserLoggedInEvent (userId, email, deviceInfo)
                    → downstream consumers: fraud detection, analytics, audit log

       └─ SessionService.issueTokens(user, deviceInfo, ipAddress)
               - generateAccessToken(user) → new JWT with fresh jti
               - RefreshTokenService.issueToken(user, ..., familyId=null)
                   - Checks active token count — if at limit (default 5), oldest is revoked
                   - New family UUID generated (null familyId = fresh session)
                   - SHA-256 hash stored in DB
               → TokenPair

       └─ AuthMapper.toAuthResponse() → builds AuthResponse DTO
```

---

### 2.3 `POST /api/v1/auth/refresh`

**What it does:** Exchanges a valid refresh token for a brand-new access token and refresh token. The old refresh token is immediately revoked.

**When to use it:** Call this automatically in your HTTP client when a request fails with `401` due to an expired access token (15-minute window). Store the new refresh token and discard the old one. The user never sees this — it is transparent session continuation.

#### Request

```json
{
  "refresh_token": "<opaque hex received at login or last refresh>"  // required
}
```

#### Response — `200 OK`

```json
{
  "data": {
    "access_token":  "<new JWT>",
    "refresh_token": "<new opaque hex>",
    "token_type":    "Bearer",
    "expires_in":    900,
    "user": { ... }
  },
  "request_id": "<UUID>"
}
```

#### Error cases

| Condition | HTTP | Code |
|---|---|---|
| Token not found | 401 | `INVALID_REFRESH_TOKEN` |
| Token expired (>30 days) | 401 | `INVALID_REFRESH_TOKEN` |
| Token already revoked — possible theft | 403 | `TOKEN_REUSE_DETECTED` |
| Account deactivated | 401 | `ACCOUNT_DEACTIVATED` |
| Account suspended | 403 | `ACCOUNT_SUSPENDED` |

#### What happens in the service layer

```
AuthController.refresh()
  │  Extracts client IP
  │
  └─ AuthFacade.refresh(request, ipAddress)
       │
       └─ SessionService.rotateRefreshToken()   [@Transactional — atomic]
            │
            ├─ RefreshTokenService.validate(rawToken)
            │       - SHA-256 hashes the raw token
            │       - Looks up by hash in DB
            │       - Not found → 401 INVALID_REFRESH_TOKEN
            │       - REVOKED already?
            │           → revokeFamily() in a REQUIRES_NEW transaction
            │             (ensures revocation commits even if this tx rolls back)
            │           → 403 TOKEN_REUSE_DETECTED
            │           → WARN log: "SECURITY: TOKEN_REUSE_DETECTED family=X userId=..."
            │       - Expired → 401 INVALID_REFRESH_TOKEN
            │
            ├─ validateAccountStatus(user)
            │       Live DB check — ensures suspended/deactivated users are blocked
            │       even if their refresh token is technically still valid
            │
            ├─ RefreshTokenService.revoke(currentToken)
            │       Sets revoked=true, revokedAt=now
            │
            ├─ JwtTokenProvider.generateAccessToken(user) → new JWT with fresh jti
            │
            └─ RefreshTokenService.issueToken(user, ..., familyId=currentToken.familyId)
                    familyId PRESERVED — all tokens in this session stay in same family
                    → new raw token returned

       └─ AuthMapper.toAuthResponseFromToken() → reads user summary from new access token
```

> **Token family & theft detection:** Every refresh token carries a `familyId`. On first login a new UUID is created; on every rotation the same UUID is carried forward. If a revoked token (one that was already rotated away) is ever presented, the entire family is revoked — both attacker and legitimate user are logged out, and a `TOKEN_REUSE_DETECTED` warning is logged. The user must re-authenticate.

---

### 2.4 `POST /api/v1/auth/logout`

**What it does:** Immediately invalidates the user's current session — the refresh token is revoked in the database and the access token's `jti` is added to the Redis denylist so it cannot be used even within its remaining 15-minute window.

**When to use it:** When the user explicitly clicks "Sign out". Always send both tokens so the session is fully closed. If you only revoke the refresh token and omit the access token, the short-lived JWT remains valid until its natural expiry.

#### Request

```
Authorization: Bearer <accessToken>
```

```json
{
  "refresh_token": "<current refresh token>"  // required
}
```

#### Response — `204 No Content`

No body.

#### What happens in the service layer

```
AuthController.logout()
  │  Reads Authorization header → extracts raw access token (strips "Bearer ")
  │  Reads authenticated userId from SecurityContext (set by JwtAuthenticationFilter)
  │
  └─ AuthFacade.logout(userId, refreshToken, accessToken)
       │
       └─ SessionService.revokeToken()   [@Transactional]
            │
            ├─ RefreshTokenService.validate(rawRefreshToken)
            │       Same validation as refresh — detects reuse, checks expiry
            │
            ├─ Ownership check: token.userId == authenticated userId
            │       Mismatch → 403 ACCESS_DENIED
            │
            ├─ RefreshTokenService.revoke(token)
            │       Sets revoked=true, revokedAt=now in DB
            │
            └─ TokenDenylistService.denyToken(jti, ttlSeconds)
                    Redis: SET "jti:<jti>" "1" EX 900
                    → access token is immediately invalid on any subsequent request
                    → if jti extraction fails (malformed token), logout still succeeds
                       but only the refresh token is revoked (warn logged)
```

---

### 2.5 `POST /api/v1/auth/verify-email`

**What it does:** Marks a user's email address as verified using the token from the verification link sent at registration.

**When to use it:** Your frontend calls this endpoint when the user lands on the `/verify-email?token=...` page after clicking the link in their inbox. The token is extracted from the URL query param and sent in the request body.

#### Request

```json
{
  "token": "<64-char hex from email link>"  // required
}
```

#### Response — `200 OK`

```json
{
  "data": {
    "message": "Email verified successfully"
  },
  "request_id": "<UUID>"
}
```

#### Error cases

| Condition | HTTP | Code |
|---|---|---|
| Token not found / hash mismatch | 400 | `INVALID_TOKEN` |
| Token already used | 400 | `INVALID_TOKEN` |
| Token expired (>24 hours) | 400 | `INVALID_TOKEN` |

#### What happens in the service layer

```
AuthController.verifyEmail()
  │
  └─ EmailVerificationService.verifyEmail(rawToken)   [@Transactional]
       │
       ├─ SHA-256 hash the raw token
       │
       ├─ tokenRepository.findByTokenHash(hash)
       │       Not found → 400 INVALID_TOKEN
       │
       ├─ token.isUsed() → 400 INVALID_TOKEN
       │
       ├─ token.isExpired(clock) → checks token.expiresAt against current time
       │       Expired → 400 INVALID_TOKEN
       │
       ├─ token.markAsUsed(clock) — sets used=true, usedAt=now
       │
       ├─ user.setEmailVerified(true)
       │
       └─ If user.status == PENDING_VERIFICATION
               → user.setStatus(ACTIVE)
               Engineers are in PENDING_VERIFICATION until email is verified
```

> **Token security:** The raw token is never stored — only its SHA-256 hash lives in the DB. The raw token travels only in the email link and the single POST request body. Even if the DB is compromised, the tokens cannot be replayed.

---

## 3. JWT Access Token

### 3.1 Token structure

| Claim | Value | Notes |
|---|---|---|
| `sub` | `UUID` (user ID) | Principal identifier |
| `jti` | `UUID` (random) | Unique per token — used for Redis denylist |
| `email` | `string` | Embedded to avoid DB lookup on every request |
| `role` | `CUSTOMER` \| `ENGINEER` \| `ADMIN` | |
| `status` | `ACTIVE` \| `SUSPENDED` \| … | Stale by up to 15 min |
| `iat` | timestamp | Issued at |
| `exp` | timestamp | 15-minute expiry |

**Algorithm:** `HS512`  
**Secret:** must be ≥ 64 bytes (`app.jwt.secret`)

### 3.2 Token validation on every request (`JwtAuthenticationFilter`)

```
Incoming request
    │
    ├─ Public path? (e.g. /auth/register, /auth/login) → skip filter
    │
    ├─ Extract Bearer token from Authorization header
    ├─ Parse + verify signature (throws 401 if invalid/expired)
    │
    ├─ Read `status` claim from token
    │   SUSPENDED / DEACTIVATED → 403 ACCOUNT_SUSPENDED        (fast path — no DB hit)
    │
    ├─ Check jti in Redis denylist
    │   Found in denylist → 401 TOKEN_REVOKED
    │   Redis unavailable → FAIL OPEN (log warn, allow request)
    │
    └─ Set SecurityContext → downstream sees authenticated user
```

> **Stale status risk:** The `status` claim is set at token-issue time. A newly suspended user can still make requests for up to **15 minutes** until their access token expires. This window is closed via the Redis denylist on logout/suspension (immediate revocation), and at refresh time by `SessionService.validateAccountStatus()` which reads the live DB.

---

## 4. Refresh Token Details

### 4.1 Token properties

| Property | Value |
|---|---|
| Format | Opaque 64-char hex (32 random bytes via `SecureRandom`) |
| Storage | SHA-256 hash stored in DB — raw token never persisted |
| Expiry | 30 days (configurable: `app.jwt.refresh-token-expiry-days`) |
| Limit | Max 5 active per user (oldest revoked automatically) |
| Family | Each token belongs to a `familyId` UUID |

### 4.2 Token family tracking & reuse detection

Every refresh token has a `familyId` (UUID). On first login, a new UUID is generated. On every rotation, the same UUID is carried forward.

```
Login
  └─ Issue token A  [familyId = X]

Refresh with A
  └─ Revoke A
  └─ Issue token B  [familyId = X]    ← same family

--- Attacker steals A and tries to use it ---

Refresh with A (already revoked)
  └─ validate() sees A is REVOKED
  └─ revokeFamily(X) → revokes B too  [REQUIRES_NEW tx — commits even if caller rolls back]
  └─ throw TOKEN_REUSE_DETECTED (403)
  └─ WARN log: "SECURITY: TOKEN_REUSE_DETECTED family=X userId=…"

Result: Both the legitimate user and attacker are logged out. Legitimate user
must re-authenticate. Attack surface closed.
```

---

## 5. Privileged Actions — `@RequiresVerifiedEmail`

Apply to any sensitive service method (payments, job booking, etc.):

```java
@RequiresVerifiedEmail
public void initiatePayment(...) { ... }
```

At runtime, `RequiresVerifiedEmailAspect` intercepts the call:

```
AOP @Before advice
    │
    ├─ Read authenticated principal from SecurityContextHolder
    ├─ Parse userId (UUID) from principal
    ├─ userRepository.findById(userId)   ← LIVE DB READ (not JWT claim)
    │
    ├─ user.emailVerified == true  → proceed
    └─ user.emailVerified != true  → throw 403 EMAIL_NOT_VERIFIED
```

> **Why not use the JWT claim?** The JWT `status` claim can be up to 15 minutes stale. For gating financial or privileged actions, always read the live DB value.

---

## 6. Token Summary Table

| Token | Format | Expiry | Storage | Revocable? |
|---|---|---|---|---|
| Access (JWT) | Signed JWT | **15 minutes** | Client only | Via Redis denylist (jti) |
| Refresh | Opaque hex | **30 days** | DB (SHA-256 hash) | DB flag + family revocation |
| Email verify | Opaque hex | **24 hours** | DB (SHA-256 hash) | Single-use (used flag) |

---

## 7. Key Configuration Properties

```yaml
app:
  jwt:
    secret: <≥64 byte string>          # HS512 signing key
    access-token-expiry-ms: 900000     # 15 minutes
    refresh-token-expiry-days: 30

  security:
    max-refresh-tokens-per-user: 5

  email-verification:
    token-expiry-hours: 24

  frontend:
    base-url: https://certifynow.co.uk

spring:
  data:
    redis:
      host: ...
      port: 6379
```

---

## 8. Required Database Migrations

```sql
-- Token family tracking (refresh token reuse detection)
ALTER TABLE refresh_token ADD COLUMN family_id UUID;
CREATE INDEX idx_refresh_token_family_id ON refresh_token(family_id);

-- Partial unique index for phone (allows multiple NULLs — users without a phone)
CREATE UNIQUE INDEX uq_users_phone_non_null
  ON users(phone)
  WHERE phone IS NOT NULL AND phone <> '';
```
