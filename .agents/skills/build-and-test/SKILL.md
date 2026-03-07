# Build and Test Workflow
## Pre-Commit / Pre-PR Checklist
ALWAYS run these commands locally before committing or pushing to a PR:
1. **Format code**: `./gradlew --no-daemon spotlessApply`
2. **Compile**: `./gradlew --no-daemon compileJava`
3. **Run tests**: `./gradlew --no-daemon test`
   All three must pass before committing. Do NOT create a PR or push commits without verifying locally first.
## Lint / Format
- Code style: Google Java Format enforced by Spotless
- Check only: `./gradlew --no-daemon spotlessCheck`
- Auto-fix: `./gradlew --no-daemon spotlessApply`
- CI runs `spotlessCheck` — if it fails, run `spotlessApply` locally and commit the changes
## Testing
- Full test suite: `./gradlew --no-daemon test`
- Integration tests use Testcontainers with PostgreSQL 16 (Docker must be running)
- Test pattern: `@SpringBootTest` + `@Testcontainers` + `@ActiveProfiles("test")`
- Tests use RestAssured for HTTP calls and JwtTokenProvider for generating auth tokens
## Key Enums and Values
- `EngineerTier`: BRONZE, SILVER, GOLD, PLATINUM (NOT "STANDARD")
- `EngineerApplicationStatus`: APPLICATION_SUBMITTED, ID_VERIFICATION_PENDING, DBS_CHECK_PENDING, INSURANCE_VERIFICATION_PENDING, TRAINING_REQUIRED, APPROVED, REJECTED
- `UserRole`: CUSTOMER, ENGINEER, ADMIN
- `UserStatus`: PENDING_VERIFICATION, ACTIVE, SUSPENDED, DEACTIVATED
## Jackson Configuration
- Global snake_case naming via `PropertyNamingStrategies.SNAKE_CASE`
- No need for `@JsonProperty` annotations on DTOs
- DTOs are Java records
## Clock Injection
- Use `Clock` bean (from `ClockConfig`) injected into services
- Never use `Instant.now()` or `OffsetDateTime.now()` directly — always use `OffsetDateTime.now(clock)`