package com.uk.certifynow.certify_now.service.share;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Service
public class ShareRenderService {

  private final TemplateEngine templateEngine;

  public ShareRenderService() {
    this.templateEngine = buildTemplateEngine();
  }

  public String renderSharePage(final SharePageModel model) {
    final Context ctx = new Context();
    ctx.setVariable("certType", model.certType());
    ctx.setVariable("propertyAddress", model.propertyAddress());
    ctx.setVariable("issuedAt", model.issuedAt());
    ctx.setVariable("expiresAt", model.expiresAt());
    ctx.setVariable("status", model.status());
    ctx.setVariable("engineerCompanyName", model.engineerCompanyName());
    ctx.setVariable("shareExpiresAt", model.shareExpiresAt());
    ctx.setVariable("documents", model.documents());
    ctx.setVariable("downloadAllUrl", model.downloadAllUrl());
    return templateEngine.process("share/certificate-share", ctx);
  }

  public String renderErrorPage() {
    final Context ctx = new Context();
    return templateEngine.process("share/certificate-share-error", ctx);
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

  public record SharePageModel(
      String certType,
      String propertyAddress,
      LocalDate issuedAt,
      LocalDate expiresAt,
      String status,
      String engineerCompanyName,
      OffsetDateTime shareExpiresAt,
      List<DocumentLink> documents,
      String downloadAllUrl) {

    public record DocumentLink(
        String fileName, String mimeType, String downloadUrl, String fileSize) {}
  }
}
