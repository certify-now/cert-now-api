package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Certificate;
import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteCertificate;
import com.uk.certifynow.certify_now.events.BeforeDeleteJob;
import com.uk.certifynow.certify_now.events.BeforeDeleteProperty;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.CertificateDTO;
import com.uk.certifynow.certify_now.repos.CertificateRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;


@Service
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final PropertyRepository propertyRepository;
    private final ApplicationEventPublisher publisher;

    public CertificateService(final CertificateRepository certificateRepository,
            final UserRepository userRepository, final JobRepository jobRepository,
            final PropertyRepository propertyRepository,
            final ApplicationEventPublisher publisher) {
        this.certificateRepository = certificateRepository;
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.propertyRepository = propertyRepository;
        this.publisher = publisher;
    }

    public List<CertificateDTO> findAll() {
        final List<Certificate> certificates = certificateRepository.findAll(Sort.by("id"));
        return certificates.stream()
                .map(certificate -> mapToDTO(certificate, new CertificateDTO()))
                .toList();
    }

    public CertificateDTO get(final UUID id) {
        return certificateRepository.findById(id)
                .map(certificate -> mapToDTO(certificate, new CertificateDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final CertificateDTO certificateDTO) {
        final Certificate certificate = new Certificate();
        mapToEntity(certificateDTO, certificate);
        return certificateRepository.save(certificate).getId();
    }

    public void update(final UUID id, final CertificateDTO certificateDTO) {
        final Certificate certificate = certificateRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(certificateDTO, certificate);
        certificateRepository.save(certificate);
    }

    public void delete(final UUID id) {
        final Certificate certificate = certificateRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        publisher.publishEvent(new BeforeDeleteCertificate(id));
        certificateRepository.delete(certificate);
    }

    private CertificateDTO mapToDTO(final Certificate certificate,
            final CertificateDTO certificateDTO) {
        certificateDTO.setId(certificate.getId());
        certificateDTO.setEpcScore(certificate.getEpcScore());
        certificateDTO.setExpiryAt(certificate.getExpiryAt());
        certificateDTO.setIssuedAt(certificate.getIssuedAt());
        certificateDTO.setValidYears(certificate.getValidYears());
        certificateDTO.setCreatedAt(certificate.getCreatedAt());
        certificateDTO.setShareTokenCreated(certificate.getShareTokenCreated());
        certificateDTO.setUpdatedAt(certificate.getUpdatedAt());
        certificateDTO.setDocumentHash(certificate.getDocumentHash());
        certificateDTO.setShareToken(certificate.getShareToken());
        certificateDTO.setCertificateNumber(certificate.getCertificateNumber());
        certificateDTO.setDocumentUrl(certificate.getDocumentUrl());
        certificateDTO.setCertificateType(certificate.getCertificateType());
        certificateDTO.setEpcRating(certificate.getEpcRating());
        certificateDTO.setResult(certificate.getResult());
        certificateDTO.setStatus(certificate.getStatus());
        certificateDTO.setMetadata(certificate.getMetadata());
        certificateDTO.setIssuedByEngineer(certificate.getIssuedByEngineer() == null ? null : certificate.getIssuedByEngineer().getId());
        certificateDTO.setJob(certificate.getJob() == null ? null : certificate.getJob().getId());
        certificateDTO.setProperty(certificate.getProperty() == null ? null : certificate.getProperty().getId());
        certificateDTO.setSupersededBy(certificate.getSupersededBy() == null ? null : certificate.getSupersededBy().getId());
        return certificateDTO;
    }

    private Certificate mapToEntity(final CertificateDTO certificateDTO,
            final Certificate certificate) {
        certificate.setEpcScore(certificateDTO.getEpcScore());
        certificate.setExpiryAt(certificateDTO.getExpiryAt());
        certificate.setIssuedAt(certificateDTO.getIssuedAt());
        certificate.setValidYears(certificateDTO.getValidYears());
        certificate.setCreatedAt(certificateDTO.getCreatedAt());
        certificate.setShareTokenCreated(certificateDTO.getShareTokenCreated());
        certificate.setUpdatedAt(certificateDTO.getUpdatedAt());
        certificate.setDocumentHash(certificateDTO.getDocumentHash());
        certificate.setShareToken(certificateDTO.getShareToken());
        certificate.setCertificateNumber(certificateDTO.getCertificateNumber());
        certificate.setDocumentUrl(certificateDTO.getDocumentUrl());
        certificate.setCertificateType(certificateDTO.getCertificateType());
        certificate.setEpcRating(certificateDTO.getEpcRating());
        certificate.setResult(certificateDTO.getResult());
        certificate.setStatus(certificateDTO.getStatus());
        certificate.setMetadata(certificateDTO.getMetadata());
        final User issuedByEngineer = certificateDTO.getIssuedByEngineer() == null ? null : userRepository.findById(certificateDTO.getIssuedByEngineer())
                .orElseThrow(() -> new NotFoundException("issuedByEngineer not found"));
        certificate.setIssuedByEngineer(issuedByEngineer);
        final Job job = certificateDTO.getJob() == null ? null : jobRepository.findById(certificateDTO.getJob())
                .orElseThrow(() -> new NotFoundException("job not found"));
        certificate.setJob(job);
        final Property property = certificateDTO.getProperty() == null ? null : propertyRepository.findById(certificateDTO.getProperty())
                .orElseThrow(() -> new NotFoundException("property not found"));
        certificate.setProperty(property);
        final Certificate supersededBy = certificateDTO.getSupersededBy() == null ? null : certificateRepository.findById(certificateDTO.getSupersededBy())
                .orElseThrow(() -> new NotFoundException("supersededBy not found"));
        certificate.setSupersededBy(supersededBy);
        return certificate;
    }

    @EventListener(BeforeDeleteUser.class)
    public void on(final BeforeDeleteUser event) {
        final ReferencedException referencedException = new ReferencedException();
        final Certificate issuedByEngineerCertificate = certificateRepository.findFirstByIssuedByEngineerId(event.getId());
        if (issuedByEngineerCertificate != null) {
            referencedException.setKey("user.certificate.issuedByEngineer.referenced");
            referencedException.addParam(issuedByEngineerCertificate.getId());
            throw referencedException;
        }
    }

    @EventListener(BeforeDeleteJob.class)
    public void on(final BeforeDeleteJob event) {
        final ReferencedException referencedException = new ReferencedException();
        final Certificate jobCertificate = certificateRepository.findFirstByJobId(event.getId());
        if (jobCertificate != null) {
            referencedException.setKey("job.certificate.job.referenced");
            referencedException.addParam(jobCertificate.getId());
            throw referencedException;
        }
    }

    @EventListener(BeforeDeleteProperty.class)
    public void on(final BeforeDeleteProperty event) {
        final ReferencedException referencedException = new ReferencedException();
        final Certificate propertyCertificate = certificateRepository.findFirstByPropertyId(event.getId());
        if (propertyCertificate != null) {
            referencedException.setKey("property.certificate.property.referenced");
            referencedException.addParam(propertyCertificate.getId());
            throw referencedException;
        }
    }

    @EventListener(BeforeDeleteCertificate.class)
    public void on(final BeforeDeleteCertificate event) {
        final ReferencedException referencedException = new ReferencedException();
        final Certificate supersededByCertificate = certificateRepository.findFirstBySupersededByIdAndIdNot(event.getId(), event.getId());
        if (supersededByCertificate != null) {
            referencedException.setKey("certificate.certificate.supersededBy.referenced");
            referencedException.addParam(supersededByCertificate.getId());
            throw referencedException;
        }
    }

}
