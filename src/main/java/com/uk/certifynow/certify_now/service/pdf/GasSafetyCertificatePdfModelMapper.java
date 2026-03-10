package com.uk.certifynow.certify_now.service.pdf;

import com.uk.certifynow.certify_now.domain.Certificate;
import com.uk.certifynow.certify_now.domain.GasSafetyAppliance;
import com.uk.certifynow.certify_now.domain.GasSafetyRecord;
import com.uk.certifynow.certify_now.domain.embeddable.ClientDetails;
import com.uk.certifynow.certify_now.domain.embeddable.CombustionReadings;
import com.uk.certifynow.certify_now.domain.embeddable.CompanyDetails;
import com.uk.certifynow.certify_now.domain.embeddable.InstallationDetails;
import com.uk.certifynow.certify_now.domain.embeddable.TenantDetails;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Maps domain entities to the flat PDF view model. */
@Component
public class GasSafetyCertificatePdfModelMapper {

  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.UK);

  public GasSafetyCertificatePdfModel map(
      final Certificate certificate,
      final GasSafetyRecord record,
      final String qrCodeDataUri,
      final String verificationUrl) {

    return new GasSafetyCertificatePdfModel(
        mapMeta(record),
        mapCompany(record.getCompanyDetails()),
        mapClient(record.getClientDetails()),
        mapTenant(record.getTenantDetails()),
        mapInstallation(record.getInstallationDetails()),
        mapEngineer(record),
        mapAppliances(record.getAppliances()),
        mapFinalChecks(record),
        mapFaults(record),
        mapSignatures(record),
        verificationUrl,
        qrCodeDataUri);
  }

  private CertificateMeta mapMeta(final GasSafetyRecord record) {
    return new CertificateMeta(
        safe(record.getCertificateNumber()),
        safe(record.getCertificateType()),
        formatDate(record.getIssueDate()),
        formatDate(record.getNextInspectionDueOnOrBefore()),
        record.getNumberOfAppliancesTested() != null ? record.getNumberOfAppliancesTested() : 0);
  }

  private CompanyInfo mapCompany(final CompanyDetails c) {
    if (c == null) {
      return new CompanyInfo("", "", "", "", "");
    }
    return new CompanyInfo(
        safe(c.getTradingTitle()),
        joinAddress(c.getAddressLine1(), c.getAddressLine2(), c.getAddressLine3(), c.getPostCode()),
        safe(c.getGasSafeRegistrationNumber()),
        safe(c.getCompanyPhone()),
        safe(c.getCompanyEmail()));
  }

  private ClientInfo mapClient(final ClientDetails c) {
    if (c == null) {
      return new ClientInfo("", "", "", "");
    }
    return new ClientInfo(
        safe(c.getName()),
        joinAddress(c.getAddressLine1(), c.getAddressLine2(), c.getAddressLine3(), c.getPostCode()),
        safe(c.getTelephone()),
        safe(c.getEmail()));
  }

  private TenantInfo mapTenant(final TenantDetails t) {
    if (t == null) {
      return new TenantInfo("", "", "");
    }
    return new TenantInfo(safe(t.getName()), safe(t.getTelephone()), safe(t.getEmail()));
  }

  private InstallationAddress mapInstallation(final InstallationDetails d) {
    if (d == null) {
      return new InstallationAddress("", "");
    }
    return new InstallationAddress(
        safe(d.getNameOrFlat()),
        joinAddress(
            d.getAddressLine1(), d.getAddressLine2(), d.getAddressLine3(), d.getPostCode()));
  }

  private EngineerInfo mapEngineer(final GasSafetyRecord r) {
    return new EngineerInfo(
        safe(r.getEngineerName()),
        safe(r.getEngineerGasSafeNumber()),
        safe(r.getEngineerLicenceCardNumber()),
        safe(r.getTimeOfArrival()),
        safe(r.getTimeOfDeparture()),
        formatDate(r.getReportIssuedDate()));
  }

  private List<ApplianceRow> mapAppliances(final List<GasSafetyAppliance> appliances) {
    if (appliances == null) {
      return List.of();
    }
    return appliances.stream().map(this::mapAppliance).collect(Collectors.toList());
  }

  private ApplianceRow mapAppliance(final GasSafetyAppliance a) {
    final CombustionReadings cr = a.getCombustionReadings();
    return new ApplianceRow(
        a.getApplianceIndex() != null ? a.getApplianceIndex() : 0,
        safe(a.getLocation()),
        safe(a.getApplianceType()),
        safe(a.getMake()),
        safe(a.getModel()),
        safe(a.getSerialNumber()),
        safe(a.getClassificationCode()),
        Boolean.TRUE.equals(a.getLandlordsAppliance()),
        safe(a.getFlueType()),
        safe(a.getFluePerformanceTests()),
        formatDecimal(a.getOperatingPressureMbar()),
        formatDecimal(a.getBurnerPressureMbar()),
        formatDecimal(a.getHeatInputKw()),
        cr != null ? formatDecimal(cr.getCoPpm()) : "",
        cr != null ? formatDecimal(cr.getCo2Percentage()) : "",
        cr != null ? formatDecimal(cr.getCoToCo2Ratio()) : "",
        boolToYesNo(a.getSafetyDevicesCorrectOperation()),
        Boolean.TRUE.equals(a.getApplianceSafeToUse()),
        Boolean.TRUE.equals(a.getApplianceServiced()),
        safe(a.getAdditionalNotes()));
  }

  private FinalChecks mapFinalChecks(final GasSafetyRecord r) {
    return new FinalChecks(
        safe(r.getGasTightnessPass()),
        safe(r.getGasPipeWorkVisualPass()),
        safe(r.getEmergencyControlAccessible()),
        safe(r.getEquipotentialBonding()),
        safe(r.getInstallationPass()),
        safe(r.getCoAlarmFittedWorkingSameRoom()),
        safe(r.getSmokeAlarmFittedWorking()),
        safe(r.getAdditionalObservations()));
  }

  private FaultsAndRemedials mapFaults(final GasSafetyRecord r) {
    return new FaultsAndRemedials(
        safe(r.getFaultsNotes()),
        safe(r.getRemedialWorkTaken()),
        boolToYesNo(r.getWarningNoticeFixed()),
        boolToYesNo(r.getApplianceIsolated()),
        safe(r.getIsolationReason()));
  }

  private Signatures mapSignatures(final GasSafetyRecord r) {
    return new Signatures(
        safe(r.getEngineerName()),
        Boolean.TRUE.equals(r.getEngineerSigned()) ? formatDate(r.getEngineerSignedDate()) : "",
        safe(r.getEngineerGasSafeNumber()),
        safe(r.getCustomerName()),
        Boolean.TRUE.equals(r.getCustomerSigned()) ? formatDate(r.getCustomerSignedDate()) : "",
        Boolean.TRUE.equals(r.getTenantSigned()) ? formatDate(r.getTenantSignedDate()) : "",
        safe(r.getEngineerNotes()));
  }

  private static String safe(final String value) {
    return value != null ? value : "";
  }

  private static String formatDate(final LocalDate date) {
    return date != null ? date.format(DATE_FMT) : "";
  }

  private static String formatDecimal(final BigDecimal value) {
    return value != null ? value.stripTrailingZeros().toPlainString() : "";
  }

  private static String boolToYesNo(final Boolean value) {
    if (value == null) return "N/A";
    return Boolean.TRUE.equals(value) ? "YES" : "NO";
  }

  private static String joinAddress(
      final String line1, final String line2, final String line3, final String postCode) {
    return Stream.of(line1, line2, line3, postCode)
        .filter(s -> s != null && !s.isBlank())
        .collect(Collectors.joining(", "));
  }
}
