package com.uk.certifynow.certify_now.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.uk.certifynow.certify_now.service.pdf.GasSafetyCertificatePdfModel.ApplianceRow;
import com.uk.certifynow.certify_now.service.pdf.GasSafetyCertificatePdfModel.CertificateMeta;
import com.uk.certifynow.certify_now.service.pdf.GasSafetyCertificatePdfModel.ClientInfo;
import com.uk.certifynow.certify_now.service.pdf.GasSafetyCertificatePdfModel.CompanyInfo;
import com.uk.certifynow.certify_now.service.pdf.GasSafetyCertificatePdfModel.EngineerInfo;
import com.uk.certifynow.certify_now.service.pdf.GasSafetyCertificatePdfModel.FaultsAndRemedials;
import com.uk.certifynow.certify_now.service.pdf.GasSafetyCertificatePdfModel.FinalChecks;
import com.uk.certifynow.certify_now.service.pdf.GasSafetyCertificatePdfModel.InstallationAddress;
import com.uk.certifynow.certify_now.service.pdf.GasSafetyCertificatePdfModel.Signatures;
import com.uk.certifynow.certify_now.service.pdf.GasSafetyCertificatePdfModel.TenantInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for the gas safety PDF generation pipeline.
 *
 * <p>These tests bypass the Spring context and database entirely — they call the package-private
 * {@code renderHtml} and {@code buildPdfBytes} methods directly.
 *
 * <p>A snapshot test persists the generated PDF to {@code
 * src/test/resources/pdf-snapshots/gas-safety-cert.pdf} on the first run. Subsequent runs compare
 * the extracted text against the snapshot text, acting as a regression guard: if a field is
 * accidentally dropped from the template the comparison will fail.
 */
class GasSafetyCertificatePdfServiceTest {

  private static final Path SNAPSHOT_DIR = Path.of("src/test/resources/pdf-snapshots");
  private static final Path SNAPSHOT_TEXT = SNAPSHOT_DIR.resolve("gas-safety-cert.txt");

  private GasSafetyCertificatePdfService service;

  @BeforeEach
  void setUp() {
    final QrCodeService qrCodeService = new QrCodeService();
    service = new GasSafetyCertificatePdfService(null, null, qrCodeService, null, null);
    ReflectionTestUtils.setField(service, "appBaseUrl", "http://localhost:8080");
  }

  @Test
  @DisplayName("Generated PDF contains all key certificate fields")
  void buildPdfBytes_containsAllKeyFields() throws IOException {
    final GasSafetyCertificatePdfModel model = buildTestModel();

    final String html = service.renderHtml(model);
    assertThat(html).isNotBlank();

    final byte[] pdfBytes = service.buildPdfBytes(html);
    assertThat(pdfBytes).isNotEmpty();

    final String pdfText = extractText(pdfBytes);

    assertThat(pdfText).as("Certificate number").contains("CP12-TEST-001");
    assertThat(pdfText).as("Issue date").contains("07/03/2026");
    assertThat(pdfText).as("Next inspection due date").contains("07/03/2027");
    assertThat(pdfText).as("Company trading title").contains("Test Gas Engineers Ltd");
    assertThat(pdfText).as("Company Gas Safe reg number").contains("GS-REG-001");
    assertThat(pdfText).as("Client name").contains("Jane Landlord");
    assertThat(pdfText).as("Tenant name").contains("Tom Tenant");
    assertThat(pdfText).as("Engineer name").contains("John Smith");
    assertThat(pdfText).as("Engineer Gas Safe number").contains("ENG-654321");
    assertThat(pdfText).as("Appliance type").containsPattern("Combi\\s+Boiler");
    // "Utility Room" may be split across lines by the PDF text-stripper when the cell wraps
    assertThat(pdfText).as("Appliance location").contains("Utility");
    assertThat(pdfText).as("Gas tightness final check").contains("YES");
    assertThat(pdfText)
        .as("Verification URL")
        .contains("http://localhost:8080/certificates/test-cert-id");
  }

  @Test
  @DisplayName("Snapshot: PDF text content matches persisted baseline (regression guard)")
  void buildPdfBytes_snapshotTextMatchesBaseline() throws IOException {
    final GasSafetyCertificatePdfModel model = buildTestModel();
    final byte[] pdfBytes = service.buildPdfBytes(service.renderHtml(model));
    final String currentText = extractText(pdfBytes);

    if (!Files.exists(SNAPSHOT_TEXT)) {
      Files.createDirectories(SNAPSHOT_DIR);
      Files.writeString(SNAPSHOT_TEXT, currentText);
      return;
    }

    final String baselineText = Files.readString(SNAPSHOT_TEXT);
    assertThat(currentText)
        .as(
            "PDF text content has changed — if this is intentional, delete "
                + SNAPSHOT_TEXT
                + " and re-run to regenerate the baseline")
        .isEqualTo(baselineText);
  }

  @Test
  @DisplayName("PDF with no tenant section renders without error")
  void buildPdfBytes_withNoTenant_rendersSuccessfully() throws IOException {
    final GasSafetyCertificatePdfModel model =
        new GasSafetyCertificatePdfModel(
            buildTestModel().meta(),
            buildTestModel().company(),
            buildTestModel().client(),
            new TenantInfo("", "", ""),
            buildTestModel().installation(),
            buildTestModel().engineer(),
            buildTestModel().appliances(),
            buildTestModel().finalChecks(),
            buildTestModel().faults(),
            buildTestModel().signatures(),
            buildTestModel().verificationUrl(),
            buildTestModel().qrCodeDataUri());

    final byte[] pdfBytes = service.buildPdfBytes(service.renderHtml(model));
    assertThat(pdfBytes).isNotEmpty();
    final String text = extractText(pdfBytes);
    assertThat(text).doesNotContain("Tenant Signature");
  }

  @Test
  @DisplayName("PDF with multiple appliances renders all appliance rows")
  void buildPdfBytes_withMultipleAppliances_rendersAllRows() throws IOException {
    final ApplianceRow second =
        new ApplianceRow(
            2,
            "Living Room",
            "Gas Fire",
            "Valor",
            "Vison",
            "SN-99999",
            "OF",
            true,
            "Open Flued",
            "PASS",
            "18",
            "9",
            "",
            "2",
            "9.5",
            "0.0002",
            "YES",
            true,
            false,
            "All checks satisfactory");

    final GasSafetyCertificatePdfModel model =
        new GasSafetyCertificatePdfModel(
            new CertificateMeta("CP12-TEST-001", "Domestic", "07/03/2026", "07/03/2027", 2),
            buildTestModel().company(),
            buildTestModel().client(),
            buildTestModel().tenant(),
            buildTestModel().installation(),
            buildTestModel().engineer(),
            List.of(buildTestModel().appliances().get(0), second),
            buildTestModel().finalChecks(),
            buildTestModel().faults(),
            buildTestModel().signatures(),
            buildTestModel().verificationUrl(),
            buildTestModel().qrCodeDataUri());

    final byte[] pdfBytes = service.buildPdfBytes(service.renderHtml(model));
    final String text = extractText(pdfBytes);

    assertThat(text).containsPattern("Combi\\s+Boiler");
    assertThat(text).containsPattern("Gas\\s+Fire");
    // Cell wrapping may split "Utility Room" and "Living Room" across lines in the extracted text
    assertThat(text).contains("Utility");
    assertThat(text).contains("Living");
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private GasSafetyCertificatePdfModel buildTestModel() {
    final QrCodeService qr = new QrCodeService();
    final String verificationUrl = "http://localhost:8080/certificates/test-cert-id";
    final byte[] qrPng = qr.generateQrPng(verificationUrl, 120);
    final String qrDataUri =
        "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(qrPng);

    final CertificateMeta meta =
        new CertificateMeta(
            "CP12-TEST-001", "Domestic/Landlord Gas Safety Record", "07/03/2026", "07/03/2027", 1);

    final CompanyInfo company =
        new CompanyInfo(
            "Test Gas Engineers Ltd",
            "1 Engineer Lane, Birmingham, B1 2AB",
            "GS-REG-001",
            "01213 000000",
            "info@testgas.com");

    final ClientInfo client =
        new ClientInfo(
            "Jane Landlord",
            "10 Downing Street, London, SW1A 2AA",
            "07700 900000",
            "jane@landlord.com");

    final TenantInfo tenant = new TenantInfo("Tom Tenant", "07700 900001", "tom@tenant.com");

    final InstallationAddress installation =
        new InstallationAddress("Flat 1", "10 Downing Street, London, SW1A 2AA");

    final EngineerInfo engineer =
        new EngineerInfo("John Smith", "ENG-654321", "LC-00123", "09:00", "10:30", "07/03/2026");

    final ApplianceRow appliance =
        new ApplianceRow(
            1,
            "Utility Room",
            "Combi Boiler",
            "Worcester Bosch",
            "Greenstar 30i",
            "SN-12345",
            "CF",
            true,
            "Room Sealed Fan Assisted",
            "PASS",
            "20",
            "10.5",
            "24",
            "15",
            "9.2",
            "0.0016",
            "YES",
            true,
            true,
            "All checks satisfactory");

    final FinalChecks checks =
        new FinalChecks("YES", "YES", "YES", "YES", "YES", "YES", "YES", "No issues found");

    final FaultsAndRemedials faults = new FaultsAndRemedials("None", "None", "N/A", "NO", "");

    final Signatures signatures =
        new Signatures(
            "John Smith",
            "07/03/2026",
            "ENG-654321",
            "Jane Landlord",
            "07/03/2026",
            "",
            "All appliances in good condition.");

    return new GasSafetyCertificatePdfModel(
        meta,
        company,
        client,
        tenant,
        installation,
        engineer,
        List.of(appliance),
        checks,
        faults,
        signatures,
        verificationUrl,
        qrDataUri);
  }

  private static String extractText(final byte[] pdfBytes) throws IOException {
    try (PDDocument doc = PDDocument.load(pdfBytes)) {
      return new PDFTextStripper().getText(doc);
    }
  }
}
