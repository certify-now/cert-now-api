package com.uk.certifynow.certify_now.service.inspection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uk.certifynow.certify_now.domain.Certificate;
import com.uk.certifynow.certify_now.domain.EpcAssessment;
import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.embeddable.ClientDetails;
import com.uk.certifynow.certify_now.domain.embeddable.EpcPropertyDetails;
import com.uk.certifynow.certify_now.domain.embeddable.OccupierDetails;
import com.uk.certifynow.certify_now.events.CertificateIssuedEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.repos.CertificateRepository;
import com.uk.certifynow.certify_now.repos.EpcAssessmentRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.rest.dto.inspection.EpcBookingDetailsRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.EpcClientDetailsRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.EpcDocumentsRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.EpcOccupierDetailsRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.EpcPhotosRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.EpcPreAssessmentRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.EpcPropertyDetailsRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.EpcRecordRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.EpcRecordResponse;
import com.uk.certifynow.certify_now.rest.dto.inspection.EpcRecordResponse.DocumentsSummary;
import com.uk.certifynow.certify_now.rest.dto.inspection.EpcRecordResponse.PhotosSummary;
import com.uk.certifynow.certify_now.rest.dto.inspection.EpcRecordResponse.PreAssessmentSummary;
import com.uk.certifynow.certify_now.service.job.JobStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EpcInspectionService {

    private static final Logger log = LoggerFactory.getLogger(EpcInspectionService.class);

    // Static mapper — ObjectMapper is not a registered Spring bean in this project
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final EpcAssessmentRepository epcAssessmentRepository;
    private final JobRepository jobRepository;
    private final CertificateRepository certificateRepository;
    private final ApplicationEventPublisher publisher;

    public EpcInspectionService(
            final EpcAssessmentRepository epcAssessmentRepository,
            final JobRepository jobRepository,
            final CertificateRepository certificateRepository,
            final ApplicationEventPublisher publisher) {
        this.epcAssessmentRepository = epcAssessmentRepository;
        this.jobRepository = jobRepository;
        this.certificateRepository = certificateRepository;
        this.publisher = publisher;
    }

    // ────────────────────────────────────────────────────────────────────────────
    // SUBMIT
    // ────────────────────────────────────────────────────────────────────────────

    @CacheEvict(value = "jobs", key = "#jobId")
    @Transactional
    public EpcRecordResponse submitEpcRecord(
            final UUID jobId, final UUID engineerId, final EpcRecordRequest request) {

        // 1. Load job
        final Job job = jobRepository
                .findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));

        // 2. Validate job is in COMPLETED status
        final JobStatus currentStatus = JobStatus.fromString(job.getStatus());
        if (currentStatus != JobStatus.COMPLETED) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "JOB_NOT_COMPLETED",
                    "Job must be in COMPLETED status to submit inspection data. Current status: "
                            + job.getStatus());
        }

        // 3. Validate certificate type is EPC
        if (!"EPC".equals(job.getCertificateType())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_CERTIFICATE_TYPE",
                    "This endpoint only accepts EPC jobs. Job type: " + job.getCertificateType());
        }

        // 4. Validate the authenticated user is the assigned engineer
        if (job.getEngineer() == null || !job.getEngineer().getId().equals(engineerId)) {
            throw new AccessDeniedException("Only the assigned engineer can submit inspection data");
        }

        // 5. Validate no existing EPC record for this job
        if (epcAssessmentRepository.existsByJobId(jobId)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_SUBMISSION",
                    "An EPC assessment record already exists for this job");
        }

        // 6. Map request → entity, save
        final EpcAssessment assessment = mapToEntity(job, request);
        final EpcAssessment saved = epcAssessmentRepository.save(assessment);

        // 7. Issue Certificate entity
        final Certificate certificate = issueCertificate(job, saved);

        // 8. Supersede any existing active EPC certificates for this property
        supersedePreviousCertificates(job, certificate);

        // 9. Transition job to CERTIFIED within this transaction (atomic)
        job.setStatus("CERTIFIED");
        job.setCertifiedAt(OffsetDateTime.now());
        job.setUpdatedAt(OffsetDateTime.now());
        jobRepository.save(job);

        // 10. Publish CertificateIssuedEvent (downstream notifications)
        publisher.publishEvent(
                new CertificateIssuedEvent(jobId, certificate.getId(), job.getProperty().getId(), "EPC"));

        log.info("EPC assessment submitted: jobId={}, assessmentId={}", jobId, saved.getId());

        return toResponse(saved);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // GET
    // ────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EpcRecordResponse getEpcRecord(final UUID jobId) {
        final EpcAssessment record = epcAssessmentRepository
                .findByJobId(jobId)
                .orElseThrow(
                        () -> new EntityNotFoundException("No EPC assessment found for job: " + jobId));
        return toResponse(record);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ────────────────────────────────────────────────────────────────────────────

    private Certificate issueCertificate(final Job job, final EpcAssessment assessment) {
        final Certificate certificate = new Certificate();
        final String certNumber = "EPC-" + job.getReferenceNumber() + "-" + LocalDate.now().getYear();
        certificate.setCertificateNumber(certNumber);
        certificate.setCertificateType("EPC");
        certificate.setIssuedAt(LocalDate.now());
        certificate.setExpiryAt(LocalDate.now().plusYears(10));
        certificate.setStatus("ACTIVE");
        certificate.setResult("PASS");
        certificate.setJob(job);
        certificate.setProperty(job.getProperty());
        certificate.setIssuedByEngineer(job.getEngineer());
        certificate.setCreatedAt(OffsetDateTime.now());
        certificate.setUpdatedAt(OffsetDateTime.now());
        return certificateRepository.save(certificate);
    }

    private void supersedePreviousCertificates(final Job job, final Certificate newCertificate) {
        final UUID propertyId = job.getProperty().getId();
        final List<Certificate> activeCerts = certificateRepository.findByPropertyIdAndCertificateTypeAndStatus(
                propertyId, "EPC", "ACTIVE");
        for (final Certificate existing : activeCerts) {
            if (!existing.getId().equals(newCertificate.getId())) {
                existing.setStatus("SUPERSEDED");
                existing.setSupersededBy(newCertificate);
                existing.setUpdatedAt(OffsetDateTime.now());
                certificateRepository.save(existing);
            }
        }
    }

    private EpcAssessment mapToEntity(final Job job, final EpcRecordRequest request) {
        final EpcAssessment a = new EpcAssessment();
        a.setJob(job);

        // Property details
        final EpcPropertyDetailsRequest pd = request.propertyDetails();
        final EpcPropertyDetails propEmbed = new EpcPropertyDetails();
        propEmbed.setAddressLine1(pd.addressLine1());
        propEmbed.setAddressLine2(pd.addressLine2());
        propEmbed.setAddressLine3(pd.addressLine3());
        propEmbed.setPostcode(pd.postcode());
        propEmbed.setPropertyType(pd.propertyType());
        propEmbed.setNumberOfBedrooms(pd.numberOfBedrooms());
        propEmbed.setYearBuilt(pd.yearBuilt());
        propEmbed.setFloorLevel(pd.floorLevel());
        propEmbed.setAccessNotes(pd.accessNotes());
        a.setPropertyDetails(propEmbed);

        // Client details
        final EpcClientDetailsRequest cd = request.clientDetails();
        final ClientDetails clientEmbed = new ClientDetails();
        clientEmbed.setName(cd.name());
        clientEmbed.setEmail(cd.email());
        clientEmbed.setTelephone(cd.telephone());
        a.setClientDetails(clientEmbed);
        a.setClientCompany(cd.company());

        // Occupier details
        if (request.occupierDetails() != null) {
            final EpcOccupierDetailsRequest od = request.occupierDetails();
            final OccupierDetails occEmbed = new OccupierDetails();
            occEmbed.setName(od.name());
            occEmbed.setTelephone(od.telephone());
            occEmbed.setEmail(od.email());
            occEmbed.setAccessInstructions(od.accessInstructions());
            a.setOccupierDetails(occEmbed);
        }

        // Booking details
        final EpcBookingDetailsRequest bd = request.bookingDetails();
        a.setAppointmentDate(bd.appointmentDate());
        a.setAppointmentTime(bd.appointmentTime());
        a.setNotesForAssessor(bd.notesForAssessor());

        // Pre-assessment data
        if (request.preAssessmentData() != null) {
            final EpcPreAssessmentRequest pa = request.preAssessmentData();
            a.setWallType(pa.wallType());
            a.setRoofInsulationDepthMm(pa.roofInsulationDepthMm());
            a.setWindowType(pa.windowType());
            a.setBoilerMake(pa.boilerMake());
            a.setBoilerModel(pa.boilerModel());
            a.setBoilerAge(pa.boilerAge());
            a.setHeatingControls(toJson(pa.heatingControls()));
            a.setSecondaryHeating(pa.secondaryHeating());
            a.setHotWaterCylinderPresent(pa.hotWaterCylinderPresent());
            a.setCylinderInsulation(pa.cylinderInsulation());
            a.setLightingLowEnergyCount(pa.lightingLowEnergyCount());
            if (pa.renewables() != null) {
                a.setRenewablesSolarPv(pa.renewables().solarPv());
                a.setRenewablesSolarThermal(pa.renewables().solarThermal());
                a.setRenewablesHeatPump(pa.renewables().heatPump());
            }
        }

        // Photos
        if (request.photos() != null) {
            final EpcPhotosRequest ph = request.photos();
            a.setPhotosExterior(toJson(ph.exterior()));
            a.setPhotosBoiler(toJson(ph.boiler()));
            a.setPhotosBoilerDataPlate(toJson(ph.boilerDataPlate()));
            a.setPhotosHeatingControls(toJson(ph.heatingControls()));
            a.setPhotosRadiators(toJson(ph.radiators()));
            a.setPhotosWindows(toJson(ph.windows()));
            a.setPhotosLoft(toJson(ph.loft()));
            a.setPhotosHotWaterCylinder(toJson(ph.hotWaterCylinder()));
            a.setPhotosRenewables(toJson(ph.renewables()));
            a.setPhotosOtherEvidence(toJson(ph.otherEvidence()));
        }

        // Documents
        if (request.documents() != null) {
            final EpcDocumentsRequest docs = request.documents();
            a.setDocPreviousEpcPdf(docs.previousEpcPdf());
            a.setDocFensaCertificate(docs.fensaCertificate());
            a.setDocLoftInsulationCertificate(docs.loftInsulationCertificate());
            a.setDocBoilerInstallationCertificate(docs.boilerInstallationCertificate());
        }

        return a;
    }

    private EpcRecordResponse toResponse(final EpcAssessment a) {
        final EpcPropertyDetails pd = a.getPropertyDetails();
        final ClientDetails cd = a.getClientDetails();
        final OccupierDetails od = a.getOccupierDetails();

        final PreAssessmentSummary pre = new PreAssessmentSummary(
                a.getWallType(),
                a.getRoofInsulationDepthMm(),
                a.getWindowType(),
                a.getBoilerMake(),
                a.getBoilerModel(),
                a.getBoilerAge(),
                fromJson(a.getHeatingControls()),
                a.getSecondaryHeating(),
                a.getHotWaterCylinderPresent(),
                a.getCylinderInsulation(),
                a.getLightingLowEnergyCount(),
                a.getRenewablesSolarPv(),
                a.getRenewablesSolarThermal(),
                a.getRenewablesHeatPump());

        final PhotosSummary photos = new PhotosSummary(
                fromJson(a.getPhotosExterior()),
                fromJson(a.getPhotosBoiler()),
                fromJson(a.getPhotosBoilerDataPlate()),
                fromJson(a.getPhotosHeatingControls()),
                fromJson(a.getPhotosRadiators()),
                fromJson(a.getPhotosWindows()),
                fromJson(a.getPhotosLoft()),
                fromJson(a.getPhotosHotWaterCylinder()),
                fromJson(a.getPhotosRenewables()),
                fromJson(a.getPhotosOtherEvidence()));

        final DocumentsSummary documents = new DocumentsSummary(
                a.getDocPreviousEpcPdf(),
                a.getDocFensaCertificate(),
                a.getDocLoftInsulationCertificate(),
                a.getDocBoilerInstallationCertificate());

        return new EpcRecordResponse(
                a.getId(),
                a.getJob().getId(),
                pd == null ? null : pd.getAddressLine1(),
                pd == null ? null : pd.getAddressLine2(),
                pd == null ? null : pd.getAddressLine3(),
                pd == null ? null : pd.getPostcode(),
                pd == null ? null : pd.getPropertyType(),
                pd == null ? null : pd.getNumberOfBedrooms(),
                pd == null ? null : pd.getYearBuilt(),
                pd == null ? null : pd.getFloorLevel(),
                pd == null ? null : pd.getAccessNotes(),
                cd == null ? null : cd.getName(),
                cd == null ? null : cd.getEmail(),
                cd == null ? null : cd.getTelephone(),
                a.getClientCompany(),
                od == null ? null : od.getName(),
                od == null ? null : od.getTelephone(),
                od == null ? null : od.getEmail(),
                od == null ? null : od.getAccessInstructions(),
                a.getAppointmentDate(),
                a.getAppointmentTime(),
                a.getNotesForAssessor(),
                pre,
                photos,
                documents,
                a.getDateCreated());
    }

    /** Serialises a list to a JSON string for text column storage. */
    private String toJson(final List<String> list) {
        if (list == null || list.isEmpty())
            return null;
        try {
            return MAPPER.writeValueAsString(list);
        } catch (final Exception e) {
            log.warn("Failed to serialise list to JSON", e);
            return null;
        }
    }

    /** Deserialises a JSON string back to a list of strings. */
    private List<String> fromJson(final String json) {
        if (json == null || json.isBlank())
            return Collections.emptyList();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (final Exception e) {
            log.warn("Failed to deserialise JSON list: {}", json, e);
            return Collections.emptyList();
        }
    }
}
