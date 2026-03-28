# Build & Test — cert-now-api

## Pre-commit Checklist
1. `./gradlew --no-daemon spotlessApply` — format (Google Java Format via Spotless)
2. `./gradlew --no-daemon compileJava` — verify compilation
3. `./gradlew --no-daemon test` — run full test suite (requires Docker for Testcontainers)

## Lint / Format
- Formatter: **Google Java Format** enforced by Spotless plugin
- Run `spotlessApply` to auto-fix; `spotlessCheck` to verify without changes
- Unused imports are removed automatically on format

## Testing
- Unit tests use H2 in-memory DB (`testRuntimeOnly('com.h2database:h2')`)
- Integration tests use **Testcontainers** (PostgreSQL) — Docker must be running
- Cucumber BDD tests live under `src/test/resources/features/`

## Key Enums (store as `String` in DB, compare via `.name()`)
- `JobStatus`: CREATED, AWAITING_ACCEPTANCE, MATCHED, ACCEPTED, EN_ROUTE, IN_PROGRESS, COMPLETED, CERTIFIED, FAILED, CANCELLED, ESCALATED
- `CertificateStatus`: ACTIVE, EXPIRED, SUPERSEDED
- `CertificateType`: GAS_SAFETY, EICR, PAT, EPC, FIRE_RISK_ASSESSMENT, BOILER_SERVICE, LEGIONELLA_RISK_ASSESSMENT, CUSTOM
- `UserRole`: CUSTOMER, ENGINEER, ADMIN

## Jackson Configuration
- Spring Boot 4.x uses **Jackson 3.x**: import from `tools.jackson.*`, NOT `com.fasterxml.jackson.*`
- `ObjectMapper` bean is auto-configured; inject it, do not instantiate manually

## Clock Injection
- Never call `OffsetDateTime.now()` or `LocalDate.now()` directly in services/schedulers
- Inject `java.time.Clock` via constructor: `private final Clock clock;`
- Use `OffsetDateTime.now(clock)` / `LocalDate.now(clock)` for testable time
- Tests override with `Clock.fixed(instant, ZoneOffset.UTC)`
