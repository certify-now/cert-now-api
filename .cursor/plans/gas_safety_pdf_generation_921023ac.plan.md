---
name: Gas Safety PDF Generation
overview: Design and implement a production-ready, async, event-driven PDF generation flow for GAS_SAFETY certificates, triggered post-commit via `CertificateIssuedEvent`, storing the PDF via a stubbed `DocumentStorageService` and updating `Certificate.documentUrl` in a separate transaction.
todos:
  - id: deps
    content: Add openhtmltopdf-pdfbox, zxing (core+javase), thymeleaf standalone, and spring-retry to build.gradle
    status: completed
  - id: async-config
    content: Create PdfAsyncConfig.java defining the pdfTaskExecutor thread pool and enable @EnableRetry
    status: completed
  - id: storage-interface
    content: Create DocumentStorageService interface and StubDocumentStorageService implementation (writes to /tmp, returns file:// URL)
    status: completed
  - id: qr-service
    content: Create QrCodeService.java using ZXing to generate a QR PNG as byte[]
    status: completed
  - id: view-model
    content: Create GasSafetyCertificatePdfModel.java (nested Java records) and GasSafetyCertificatePdfModelMapper.java
    status: completed
  - id: pdf-service
    content: Create CertificatePdfService interface and GasSafetyCertificatePdfService implementation (Thymeleaf render → OpenHTMLtoPDF → store → update documentUrl)
    status: completed
  - id: html-template
    content: Create resources/templates/pdf/gas-safety-certificate.html — A4 portrait Thymeleaf template with all sections
    status: completed
  - id: event-listener
    content: Update CertificateIssuedEventListener to inject CertificatePdfService, add @Async("pdfTaskExecutor"), dispatch for GAS_SAFETY with try/catch
    status: completed
  - id: unit-tests
    content: Write GasSafetyCertificatePdfServiceTest (text assertions + snapshot) and QrCodeServiceTest
    status: completed
  - id: integration-test
    content: Write GasSafetyPdfIntegrationTest using Awaitility to assert documentUrl and qrCodeUrl populated after form submission
    status: completed
isProject: false
---

# Gas Safety PDF Certificate Generation

## 1. Library Selection

**PDF rendering: OpenHTMLtoPDF (`com.openhtmltopdf:openhtmltopdf-pdfbox`)**

- License: LGPL 2.1 (safe for commercial SaaS — no obligation to open-source your application code, only modifications to the library itself)
- Renders XHTML + CSS to PDF via Apache PDFBox backend (Apache 2.0)
- HTML/CSS template is maintained independently of Java code — a design change requires no recompilation
- Ruled out: iText 7 Community (AGPL — forces you to open-source your app or buy a ~£10k/year commercial licence); raw PDFBox API (verbose programmatic layout); JasperReports (heavy XML design files, overkill)

**QR code: ZXing** (`com.google.zxing:core` + `javase`, Apache 2.0) — industry standard, no licensing concerns

**Template engine: Thymeleaf standalone** (`org.thymeleaf:thymeleaf`, Apache 2.0) — renders the HTML template with model values before handing to OpenHTMLtoPDF; avoids string concatenation in Java

---

## 2. New Dependencies (`build.gradle`)

```gradle
implementation 'com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10'
implementation 'com.google.zxing:core:3.5.3'
implementation 'com.google.zxing:javase:3.5.3'
implementation 'org.thymeleaf:thymeleaf:3.1.3.RELEASE'
implementation 'org.springframework.retry:spring-retry'
```

---

## 3. Files to Create / Modify

**Modified:**

- `[build.gradle](build.gradle)` — add 5 dependencies above
- `[events/CertificateIssuedEventListener.java](src/main/java/com/uk/certifynow/certify_now/events/CertificateIssuedEventListener.java)` — inject `CertificatePdfService`, add `@Async`, dispatch for `GAS_SAFETY`

**Created:**


| File                                                  | Purpose                                 |
| ----------------------------------------------------- | --------------------------------------- |
| `service/pdf/GasSafetyCertificatePdfModel.java`       | View model (nested Java records)        |
| `service/pdf/GasSafetyCertificatePdfModelMapper.java` | Domain entities → view model            |
| `service/pdf/QrCodeService.java`                      | ZXing QR PNG generation                 |
| `service/pdf/CertificatePdfService.java`              | Interface                               |
| `service/pdf/GasSafetyCertificatePdfService.java`     | Orchestration impl                      |
| `service/storage/DocumentStorageService.java`         | Interface                               |
| `service/storage/StubDocumentStorageService.java`     | Writes to `/tmp`, returns `file://` URL |
| `config/PdfAsyncConfig.java`                          | Named thread pool `pdfTaskExecutor`     |
| `resources/templates/pdf/gas-safety-certificate.html` | Thymeleaf A4 certificate template       |
| `test/…/pdf/GasSafetyCertificatePdfServiceTest.java`  | Unit — text assertions + snapshot       |
| `test/…/pdf/QrCodeServiceTest.java`                   | Unit — generate + decode                |
| `test/…/rest/GasSafetyPdfIntegrationTest.java`        | Integration — event → `documentUrl`     |


---

## 4. View Model

`GasSafetyCertificatePdfModel` is a tree of Java records — the renderer never touches JPA entities:

```java
record GasSafetyCertificatePdfModel(
    CertificateMeta meta,          // cert number, issueDate, nextDue, applianceCount
    CompanyInfo company,           // tradingTitle, address, gasSafeRegNumber, phone, email
    ClientInfo client,             // name, address, tel, email
    TenantInfo tenant,             // name, tel, email (nullable)
    InstallationAddress installation, // nameOrFlat, fullAddress
    EngineerInfo engineer,         // name, gasSafeNumber, licenceCard, arrival/departure, reportDate
    List<ApplianceRow> appliances, // one row per GasSafetyAppliance
    FinalChecks finalChecks,       // 7 YES/NO/N/A fields
    FaultsAndRemedials faults,     // faultsNotes, remedialWork, warningFixed, isolated, isolationReason
    Signatures signatures,         // engineer/customer/tenant signed + dates + notes
    String verificationUrl,        // https://app.certifynow.io/certificates/{id}
    String qrCodeBase64            // data URI, embedded inline
)
```

`ApplianceRow` includes: `index`, `location`, `type`, `make`/`model`, `flueType`, `inspectionType`, `safeToUse`, `classCode`, `operatingPressureMbar`, `burnerPressureMbar`, `coPpm`, `co2Percentage`, `coToCo2Ratio`, `serviced`, `additionalNotes`.

`GasSafetyCertificatePdfModelMapper` has one entry point:

```java
GasSafetyCertificatePdfModel map(Certificate cert, GasSafetyRecord record, String qrCodeBase64)
```

---

## 5. Service Design

```java
// Interface
interface CertificatePdfService {
    void generateAndStore(UUID certificateId);
}
```

`GasSafetyCertificatePdfService` orchestration (steps inside a single method, `documentUrl` update in its own `@Transactional(REQUIRES_NEW)`):

```
1. Load Certificate (+ job → gasSafetyRecord) from DB
2. Build verificationUrl = "${app.base-url}/certificates/{cert.id}"
3. QrCodeService.generateQrPng(verificationUrl, 200) → byte[]
4. Base64-encode QR bytes → data URI string
5. Map via GasSafetyCertificatePdfModelMapper → GasSafetyCertificatePdfModel
6. Render Thymeleaf template (ClassLoaderTemplateResolver) → HTML string
7. PdfRendererBuilder.withHtmlContent(html, baseUri).toStream(baos).run() → byte[]
8. DocumentStorageService.store(cert.id, "GAS_SAFETY", pdfBytes) → documentUrl
9. In new @Transactional(REQUIRES_NEW): set cert.documentUrl, record.qrCodeUrl, save both
```

`@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))` is placed on `generateAndStore` to handle transient PDF/upload failures without retrying the original inspection transaction.

---

## 6. Storage Stub

```java
interface DocumentStorageService {
    String store(UUID certificateId, String certificateType, byte[] content);
}
```

`StubDocumentStorageService`: writes to `${java.io.tmpdir}/cert-now-pdfs/{certificateId}.pdf`, returns `file:///tmp/cert-now-pdfs/{certificateId}.pdf`. Swap for `S3DocumentStorageService` later without changing callers.

---

## 7. Event Listener Wiring

`[CertificateIssuedEventListener.java](src/main/java/com/uk/certifynow/certify_now/events/CertificateIssuedEventListener.java)` becomes:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async("pdfTaskExecutor")
public void onCertificateIssued(CertificateIssuedEvent event) {
    if (!"GAS_SAFETY".equals(event.getCertificateType())) return;
    try {
        certificatePdfService.generateAndStore(event.getCertificateId());
    } catch (Exception e) {
        log.error("PDF generation failed for certificateId={} after retries",
                  event.getCertificateId(), e);
        // Future: publish to DLQ / alert. Original transaction is already committed — no rollback.
    }
}
```

`PdfAsyncConfig` registers a `ThreadPoolTaskExecutor` named `pdfTaskExecutor` (corePoolSize=2, maxPoolSize=5, queueCapacity=25, threadNamePrefix=`pdf-gen-`), separate from the existing matching executor.

---

## 8. PDF Layout (A4 Portrait, 15mm margins)

```
┌──────────────────────────────────────────────────────────────┐
│  [LOGO]   LANDLORD GAS SAFETY RECORD  ·  CP12               │
│           Certificate No: GS-XXXX   Issued: DD/MM/YYYY      │
├─────────────────────────┬────────────────────────────────────┤
│  COMPANY                │  CLIENT (LANDLORD)                 │
│  Trading title, address │  Name, address, tel, email         │
│  Gas Safe Reg / phone   ├────────────────────────────────────┤
├─────────────────────────┤  PROPERTY / INSTALLATION           │
│  ENGINEER               │  Flat/name, address, postcode      │
│  Name, Gas Safe No      ├────────────────────────────────────┤
│  Licence card No        │  TENANT (if applicable)            │
│  Arrival / Departure    │  Name, tel, email                  │
│  Next due: DD/MM/YYYY   │                                    │
├─────────────────────────┴────────────────────────────────────┤
│  APPLIANCES INSPECTED                                        │
│  # │ Location │ Type │ Flue │ Op.P │ CO ppm │ CO/CO₂ │ Safe│
│  1 │ Kitchen  │ Boiler│ Fan │ 20mb │  5     │ 0.004  │  ✓  │
├──────────────────────────────────────────────────────────────┤
│  FINAL SAFETY CHECKS (YES / NO / N/A grid)                   │
│  Gas Tightness · Pipework Visual · Emergency Control         │
│  Bonding · Installation Pass · CO Alarm · Smoke Alarm        │
├──────────────────────────────────────────────────────────────┤
│  DEFECTS / REMEDIAL WORK                                     │
│  [faults notes]  Warning notice fixed: YES  Isolated: NO     │
├──────────────────────────────────────────────────────────────┤
│  SIGNATURES                                                  │
│  Engineer: _____  Date: ___  Gas Safe No: XXXXXX            │
│  Customer/Landlord: _____  Date: ___                         │
│  Tenant:   _____  Date: ___                                  │
├──────────────────────────────────────────────────────────────┤
│  [QR CODE 100px]  Scan to verify · certifynow.io             │
│  Next inspection due on or before: DD/MM/YYYY                │
└──────────────────────────────────────────────────────────────┘
```

**Typography:**

- Section headers: 9pt bold, white text on `#1a365d` background
- Body text: 8pt, `Helvetica` (PDFBox built-in, no font embedding needed)
- Certificate title: 14pt bold
- Table borders: 0.5pt `#aaa`, alternating row fill `#f7f7f7` / white
- Prints cleanly in greyscale (no colour-only information)

---

## 9. Testing Strategy

**Unit — `GasSafetyCertificatePdfServiceTest`:**

- Build a fully-populated `GasSafetyCertificatePdfModel` via a `TestModelFactory`
- Call `renderPdf(model)` → `byte[]`
- Load result with `PDDocument.load(bytes)` + `PDFTextStripper` → assert presence of: certificate number, engineer name, Gas Safe reg, appliance type, `"YES"` safety checks, tenant name, verification URL text
- **Snapshot test**: first run writes `src/test/resources/pdf-snapshots/gas-safety.pdf`; subsequent runs text-strip and compare — catches fields silently removed from the template

**Unit — `QrCodeServiceTest`:**

- Generate QR PNG for `https://app.certifynow.io/certificates/test-id`
- Decode with ZXing `QRCodeReader` → assert decoded URL equals input

**Integration — `GasSafetyPdfIntegrationTest`** (extends existing Testcontainers PostgreSQL setup):

- Submit full gas safety form via REST (reuse helper from `GasSafetyInspectionIntegrationTest`)
- Awaitility (max 5s, 200ms poll) on `certificateRepository.findById(certId).get().getDocumentUrl()`
- Assert `documentUrl` is not blank
- Assert `gasSafetyRecordRepository.findByJobId(jobId).get().getQrCodeUrl()` is not blank

