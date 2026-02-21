# CertifyNow — Authentication & Registration Flow

## 1. High-Level Architecture

```
HTTP Request
    │
    ▼
AuthController          ← REST layer: maps HTTP → facade calls
    │
    ▼
AuthFacade              ← Orchestration only, zero business logic
    ├── RegistrationService    ← user creation
    ├── AuthenticationService  ← credential validation
    ├── SessionService         ← token lifecycle
    └── AuthMapper             ← entity → DTO
```

---

## 2. Registration Flow

### 2.1 Request path

```
POST /api/v1/auth/register
{
  "email": "...",
  "password": "...",
  "fullName": "...",
  "phone": "...",    // optional
  "role": "CUSTOMER" | "ENGINEER"
}
```

**IP address** is extracted via `IpAddressUtils.extractClientIp()` which safely reads the first valid token from `X-Forwarded-For`, validates it with `InetAddress`, and falls back to `request.getRemoteAddr()` *(Fix 6)*.

### 2.2 Step-by-step inside a single `@Transactional` boundary

```
AuthController
  └─ AuthFacade.register()
       └─ RegistrationService.registerUser()   [entire method is @Transactional]
            │
            ├─ 1. Duplicate email check (case-insensitive)
            │       Duplicate found?
            │        YES → publish DuplicateRegistrationAttemptEvent (async)
            │              return Optional.empty()                  ← Fix 3
            │        NO  → continue
            │
            ├─ 2. Duplicate phone check (only if phone != null && !blank)
            │       Duplicate found?                                ← Fix 7
            │        YES → publish DuplicateRegistrationAttemptEvent
            │              return Optional.empty()
            │        NO  → continue
            │
            ├─ 3. UserFactory.createEmailUser()
            │       - BCrypt-hashes the password
            │       - Sets role, auth provider = EMAIL
            │       - Sets status:
            │           CUSTOMER  → ACTIVE
            │           ENGINEER  → PENDING_VERIFICATION
            │       - Sets emailVerified = false
            │
            ├─ 4. userRepository.save(user)
            │
            ├─ 5. ProfileFactory → create + save role-specific profile
            │       CUSTOMER → CustomerProfile  (compliance score, letting agent flag…)
            │       ENGINEER → EngineerProfile  (tier, bio, service radius…)
            │       ADMIN    → no profile
            │
            ├─ 6. Create UserConsent records (TERMS_OF_SERVICE + PRIVACY_POLICY)
            │       Stores IP address for audit trail
            │
            └─ 7. Publish UserRegisteredEvent
                    ↓  (TRANSACTION COMMITS HERE)
                    ↓
            EmailVerificationEventListener   [AFTER_COMMIT] ← Fix 4
                    └─ EmailVerificationService.sendVerificationEmail()
                            - Deletes old unused tokens for user
                            - Generates 64-char hex token (SecureRandom)
                            - SHA-256 hashes it before storing in DB
                            - Sends email with link: {frontend}/verify-email?token=<raw>
```

> **Why AFTER_COMMIT?** If the SMTP call is inside the transaction and fails, the entire user creation rolls back. By listening after commit *(Fix 4)*, the user record is always saved even if the email provider is down.

### 2.3 Silent duplicate — email enumeration prevention *(Fix 3)*

If the email already exists, the response to the caller is **identical** to a successful registration — no 409, no error message that leaks whether an account exists.

```
                  Duplicate email
                        │
                        ▼
           DuplicateRegistrationAttemptEvent
                        │
                        ▼ (async @EventListener)
           RegistrationNotificationListener
                        │
                        ▼
           EmailService.sendDuplicateRegistrationNotification()
           → "Someone tried to register with your email. If this was you…"
```

The facade returns `authMapper.toGenericRegistrationResponse()` — null tokens, null user summary — and the client should always show *"Check your inbox to verify your account"*.

### 2.4 AuthFacade — happy path response

```
Optional<User> present?
    YES → SessionService.issueTokens()   ← generates access + refresh token pair
          AuthMapper.toAuthResponse()    ← builds full AuthResponse DTO
          → 201 with tokens + user summary

    NO  → AuthMapper.toGenericRegistrationResponse()
          → 201 with nulls (indistinguishable from real success)
```

---

## 3. Email Verification Flow

```
User clicks link: GET /api/v1/auth/verify-email?token=<raw64charHex>
                        │
                        ▼
           EmailVerificationService.verifyEmail(rawToken)
                        │
                        ├─ SHA-256 hash the raw token
                        ├─ Look up EmailVerificationToken by hash
                        ├─ Check: not already used
                        ├─ Check: not expired (24h window)
                        │
                        ├─ token.markAsUsed()
                        ├─ user.setEmailVerified(true)
                        └─ if status == PENDING_VERIFICATION → set ACTIVE
```

---

## 4. JWT Access Token

### 4.1 Token structure

| Claim | Value | Notes |
|---|---|---|
| `sub` | `UUID` (user ID) | Principal identifier |
| `jti` | `UUID` (random) | Unique per token — used for denylist |
| `email` | `string` | Embedded to avoid DB lookup on every request |
| `role` | `CUSTOMER` \| `ENGINEER` \| `ADMIN` | |
| `status` | `ACTIVE` \| `SUSPENDED` \| … | Stale by up to 15 min |
| `iat` | timestamp | Issued at |
| `exp` | timestamp | 15-minute expiry |

**Algorithm:** `HS512`  
**Secret:** must be ≥ 64 bytes (`app.jwt.secret`)

### 4.2 Token validation on every request (`JwtAuthenticationFilter`)

```
Incoming request
    │
    ├─ Public path? (e.g. /auth/register, /auth/login) → skip filter
    │
    ├─ Extract Bearer token from Authorization header
    ├─ Parse + verify signature (throws 401 if invalid/expired)
    │
    ├─ Read `status` claim from token
    │   SUSPENDED / DEACTIVATED → 403 ACCOUNT_SUSPENDED        ← Fix 2 (fast path)
    │
    ├─ Check jti in Redis denylist                              ← Fix 1
    │   Found in denylist → 401 TOKEN_REVOKED
    │   Redis unavailable → FAIL OPEN (log warn, allow request)
    │
    └─ Set SecurityContext → downstream sees authenticated user
```

> **Stale status risk:** The `status` claim in the JWT is set at token-issue time. A newly suspended user can still make requests for up to **15 minutes** until their access token expires. This is closed for **logged-in sessions** via the Redis denylist (immediate revocation on logout/suspension), and for **refresh** by `SessionService.validateAccountStatus()` which reads the live DB.

---

## 5. Refresh Token

### 5.1 Token properties

| Property | Value |
|---|---|
| Format | Opaque 64-char hex (32 random bytes via `SecureRandom`) |
| Storage | SHA-256 hash stored in DB — raw token never persisted |
| Expiry | 30 days (configurable: `app.jwt.refresh-token-expiry-days`) |
| Limit | Max 5 active per user (oldest revoked automatically) |
| Family | Each token belongs to a `familyId` UUID |

### 5.2 Token rotation (silent re-authentication)

```
POST /api/v1/auth/refresh  { "refreshToken": "<raw>" }
                │
                ▼
   SessionService.rotateRefreshToken()   [@Transactional — atomic]
                │
                ├─ RefreshTokenService.validate(rawToken)
                │       - Hash raw token → SHA-256
                │       - Look up by hash in DB
                │       - REVOKED? → revokeFamily() + throw TOKEN_REUSE_DETECTED  ← Fix 5
                │       - EXPIRED? → throw INVALID_REFRESH_TOKEN
                │
                ├─ validateAccountStatus(user)                   ← Fix 2
                │       DEACTIVATED → 401
                │       SUSPENDED   → 403
                │
                ├─ RefreshTokenService.revoke(currentToken)
                │
                ├─ JwtTokenProvider.generateAccessToken(user)    ← new jti
                │
                └─ RefreshTokenService.issueToken(user, ..., familyId)
                          familyId PRESERVED from currentToken   ← Fix 5
                          → new raw token returned to client
```

### 5.3 Token family tracking & reuse detection *(Fix 5)*

Every refresh token has a `familyId` (UUID). On first login, a new UUID is generated. On every rotation, the same UUID is carried forward.

```
Login
  └─ Issue token A  [familyId = X]

Refresh with A
  └─ Revoke A
  └─ Issue token B  [familyId = X]    ← same family

--- Attacker steals A and tries to use it ---

Refresh with A (revoked)
  └─ validate() sees A is REVOKED
  └─ revokeFamily(X) → revokes B too
  └─ throw TOKEN_REUSE_DETECTED (403)
  └─ WARN log: "SECURITY: TOKEN_REUSE_DETECTED family=X userId=…"
  
Result: Both the legitimate user and attacker are logged out. Legitimate user
must re-authenticate. Attack surface closed.
```

---

## 6. Logout

```
POST /api/v1/auth/logout
Headers: Authorization: Bearer <accessToken>
Body: { "refreshToken": "..." }
                │
                ▼
   AuthController → extracts Bearer token from header
   AuthFacade.logout(userId, refreshToken, accessToken)
                │
                ▼
   SessionService.revokeToken()
                │
                ├─ RefreshTokenService.validate(rawRefreshToken)
                ├─ Verify token.userId == authenticated userId
                ├─ RefreshTokenService.revoke(token)      ← marks revoked in DB
                │
                └─ TokenDenylistService.denyToken(jti, ttlSeconds)
                          Redis SET "jti:<jti>" "1" EX 900
                          → access token immediately invalid   ← Fix 1
```

---

## 7. Privileged Actions — `@RequiresVerifiedEmail` *(Fix 8)*

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

## 8. Token Summary Table

| Token | Format | Expiry | Storage | Revocable? |
|---|---|---|---|---|
| Access (JWT) | Signed JWT | **15 minutes** | Client only | Via Redis denylist (jti) |
| Refresh | Opaque hex | **30 days** | DB (SHA-256 hash) | DB flag + family revocation |
| Email verify | Opaque hex | **24 hours** | DB (SHA-256 hash) | Single-use (used flag) |

---

## 9. Key Configuration Properties

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

## 10. Required Database Migration

```sql
-- Fix 5: Token family tracking
ALTER TABLE refresh_token ADD COLUMN family_id UUID;
CREATE INDEX idx_refresh_token_family_id ON refresh_token(family_id);

-- Fix 7: Partial unique index for phone (allows multiple NULLs)
CREATE UNIQUE INDEX uq_users_phone_non_null
  ON users(phone)
  WHERE phone IS NOT NULL AND phone <> '';
```
