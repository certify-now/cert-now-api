package com.uk.certifynow.certify_now.service.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.uk.certifynow.certify_now.domain.Certificate;
import com.uk.certifynow.certify_now.domain.GasSafetyRecord;
import com.uk.certifynow.certify_now.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.repos.CertificateRepository;
import com.uk.certifynow.certify_now.repos.GasSafetyRecordRepository;
import com.uk.certifynow.certify_now.service.storage.DocumentStorageService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Generates a CP12 / Landlord Gas Safety Certificate PDF.
 *
 * <p>Orchestration steps (all within a single {@code @Transactional} boundary so lazy associations
 * resolve, and JPA dirty-checking commits the URL updates on commit):
 *
 * <ol>
 *   <li>Load {@link Certificate} and its linked {@link GasSafetyRecord} from the database.
 *   <li>Build a verification URL and generate a QR code PNG.
 *   <li>Map domain objects to {@link GasSafetyCertificatePdfModel}.
 *   <li>Render the Thymeleaf HTML template.
 *   <li>Convert HTML to PDF bytes via OpenHTMLtoPDF.
 *   <li>Upload the PDF via {@link DocumentStorageService}.
 *   <li>Set {@code Certificate.documentUrl} and {@code GasSafetyRecord.qrCodeUrl/verificationUrl};
 *       JPA dirty-checking persists the change on transaction commit.
 * </ol>
 *
 * <p>Retries on transient failures are handled by the caller ({@code
 * CertificateIssuedEventListener}) via {@link #generateAndStore}, which contains a built-in
 * exponential back-off retry loop. Replace with a proper Spring Retry {@code @Retryable} annotation
 * when {@code spring-boot-starter-aop} is available on the classpath.
 *
 * <p>To add custom fonts for consistent cross-platform rendering, place a TTF file (e.g. {@code
 * LiberationSans-Regular.ttf}) in {@code src/main/resources/fonts/} and register it with {@code
 * PdfRendererBuilder.useFont()} in {@link #buildPdfBytes(String)}.
 */
@Service
public class GasSafetyCertificatePdfService implements CertificatePdfService {

  private static final Logger log = LoggerFactory.getLogger(GasSafetyCertificatePdfService.class);

  private static final int QR_SIZE_PIXELS = 120;
  private static final int MAX_ATTEMPTS = 3;
  private static final long INITIAL_BACKOFF_MS = 2_000L;

  /** True when the Roboto TTF files are present on the classpath — enables PDF/A-2b output. */
  private static final boolean FONTS_AVAILABLE =
      GasSafetyCertificatePdfService.class.getResource("/fonts/Roboto-Regular.ttf") != null
          && GasSafetyCertificatePdfService.class.getResource("/fonts/Roboto-Bold.ttf") != null;

  private final CertificateRepository certificateRepository;
  private final GasSafetyRecordRepository gasSafetyRecordRepository;
  private final QrCodeService qrCodeService;
  private final GasSafetyCertificatePdfModelMapper mapper;
  private final DocumentStorageService storageService;
  private final TemplateEngine templateEngine;

  @Value("${app.base-url:http://localhost:8080}")
  private String appBaseUrl;

  /**
   * Self-reference through the Spring proxy so {@code @Transactional} on {@link
   * #attemptGenerateAndStore} is honoured.
   */
  @Lazy @Autowired private GasSafetyCertificatePdfService self;

  public GasSafetyCertificatePdfService(
      final CertificateRepository certificateRepository,
      final GasSafetyRecordRepository gasSafetyRecordRepository,
      final QrCodeService qrCodeService,
      final GasSafetyCertificatePdfModelMapper mapper,
      final DocumentStorageService storageService) {
    this.certificateRepository = certificateRepository;
    this.gasSafetyRecordRepository = gasSafetyRecordRepository;
    this.qrCodeService = qrCodeService;
    this.mapper = mapper;
    this.storageService = storageService;
    this.templateEngine = buildTemplateEngine();
  }

  /**
   * Generates and stores the PDF with up to {@value #MAX_ATTEMPTS} attempts using exponential
   * back-off. Each attempt runs in its own transaction so a failed storage call does not leave a
   * partial transaction open.
   */
  @Override
  public void generateAndStore(final UUID certificateId) {
    Exception lastException = null;
    long backoffMs = INITIAL_BACKOFF_MS;

    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        self.attemptGenerateAndStore(certificateId);
        return;
      } catch (Exception e) {
        lastException = e;
        log.warn(
            "PDF generation attempt {}/{} failed for certificateId={}: {}",
            attempt,
            MAX_ATTEMPTS,
            certificateId,
            e.getMessage());
        if (attempt < MAX_ATTEMPTS) {
          sleep(backoffMs);
          backoffMs *= 2;
        }
      }
    }

    throw new RuntimeException(
        "PDF generation failed after "
            + MAX_ATTEMPTS
            + " attempts for certificateId="
            + certificateId,
        lastException);
  }

  @Transactional
  void attemptGenerateAndStore(final UUID certificateId) {
    log.info("Generating gas safety certificate PDF: certificateId={}", certificateId);

    final Certificate cert =
        certificateRepository
            .findById(certificateId)
            .orElseThrow(
                () -> new EntityNotFoundException("Certificate not found: " + certificateId));

    final UUID jobId = cert.getJob().getId();
    final GasSafetyRecord record =
        gasSafetyRecordRepository
            .findByJobIdWithAppliances(jobId)
            .orElseThrow(
                () -> new EntityNotFoundException("GasSafetyRecord not found for jobId=" + jobId));

    final String verificationUrl = appBaseUrl + "/certificates/" + certificateId;
    final String qrDataUri = qrCodeService.generateQrDataUriOrNull(verificationUrl, QR_SIZE_PIXELS);

    final GasSafetyCertificatePdfModel model = mapper.map(cert, record, qrDataUri, verificationUrl);

    final String html = renderHtml(model);
    final byte[] pdfBytes = buildPdfBytes(html);

    final String documentUrl = storageService.store(certificateId, "GAS_SAFETY", pdfBytes);

    cert.setDocumentUrl(documentUrl);
    record.setQrCodeUrl(documentUrl);
    record.setVerificationUrl(verificationUrl);

    log.info(
        "Gas safety certificate PDF stored: certificateId={} url={}", certificateId, documentUrl);
  }

  /**
   * Visible for testing — renders the Thymeleaf template to an HTML string. Exposed as
   * package-private so unit tests can call it directly without going through the full
   * storage/transaction stack.
   */
  String renderHtml(final GasSafetyCertificatePdfModel model) {
    final Context ctx = new Context();
    ctx.setVariable("m", model);
    return templateEngine.process("pdf/gas-safety-certificate", ctx);
  }

  /**
   * Visible for testing — converts an HTML string to PDF bytes using OpenHTMLtoPDF + PDFBox.
   *
   * <p>When Roboto TTF fonts are present on the classpath ({@code /fonts/Roboto-Regular.ttf} and
   * {@code /fonts/Roboto-Bold.ttf}), the builder upgrades to <strong>PDF/A-2b</strong> mode, which
   * embeds fonts, attaches XMP metadata, and satisfies Gas Safe archival requirements. Without the
   * fonts the output remains a standard PDF/1.7.
   */
  byte[] buildPdfBytes(final String html) {
    try {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      final PdfRendererBuilder builder = new PdfRendererBuilder();

      if (FONTS_AVAILABLE) {
        builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_2_B);
        builder.useFont(
            () -> getClass().getResourceAsStream("/fonts/Roboto-Regular.ttf"),
            "Roboto",
            400,
            BaseRendererBuilder.FontStyle.NORMAL,
            true);
        builder.useFont(
            () -> getClass().getResourceAsStream("/fonts/Roboto-Bold.ttf"),
            "Roboto",
            700,
            BaseRendererBuilder.FontStyle.NORMAL,
            true);
        log.debug("PDF/A-2b mode enabled with embedded Roboto fonts");
      } else {
        log.debug(
            "Roboto fonts not found on classpath — generating standard PDF without PDF/A conformance");
      }

      builder.useFastMode();
      builder.withHtmlContent(html, "http://placeholder/");
      builder.toStream(baos);
      builder.run();
      return baos.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to render PDF bytes", e);
    }
  }

  private static TemplateEngine buildTemplateEngine() {
    final ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("/templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    resolver.setCacheable(true);

    final TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    return engine;
  }

  private static void sleep(final long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }
}
