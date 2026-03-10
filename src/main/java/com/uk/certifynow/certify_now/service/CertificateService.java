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
import com.uk.certifynow.certify_now.service.mappers.CertificateMapper;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class CertificateService {

  private final CertificateRepository certificateRepository;
  private final UserRepository userRepository;
  private final JobRepository jobRepository;
  private final PropertyRepository propertyRepository;
  private final ApplicationEventPublisher publisher;
  private final CertificateMapper certificateMapper;

  public CertificateService(
      final CertificateRepository certificateRepository,
      final UserRepository userRepository,
      final JobRepository jobRepository,
      final PropertyRepository propertyRepository,
      final ApplicationEventPublisher publisher,
      final CertificateMapper certificateMapper) {
    this.certificateRepository = certificateRepository;
    this.userRepository = userRepository;
    this.jobRepository = jobRepository;
    this.propertyRepository = propertyRepository;
    this.publisher = publisher;
    this.certificateMapper = certificateMapper;
  }

  public List<CertificateDTO> findAll() {
    final List<Certificate> certificates = certificateRepository.findAll(Sort.by("id"));
    return certificates.stream().map(certificateMapper::toDTO).toList();
  }

  public CertificateDTO get(final UUID id) {
    return certificateRepository
        .findById(id)
        .map(certificateMapper::toDTO)
        .orElseThrow(NotFoundException::new);
  }

  @Transactional
  public UUID create(final CertificateDTO certificateDTO) {
    final Certificate certificate = new Certificate();
    certificateMapper.updateEntity(certificateDTO, certificate);
    resolveReferences(certificateDTO, certificate);
    UUID savedId = certificateRepository.save(certificate).getId();
    log.info("Certificate {} created (type={})", savedId, certificateDTO.getCertificateType());
    return savedId;
  }

  @Transactional
  public void update(final UUID id, final CertificateDTO certificateDTO) {
    final Certificate certificate =
        certificateRepository.findById(id).orElseThrow(NotFoundException::new);
    certificateMapper.updateEntity(certificateDTO, certificate);
    resolveReferences(certificateDTO, certificate);
    certificateRepository.save(certificate);
    log.info("Certificate {} updated", id);
  }

  @Transactional
  public void delete(final UUID id) {
    final Certificate certificate =
        certificateRepository.findById(id).orElseThrow(NotFoundException::new);
    publisher.publishEvent(new BeforeDeleteCertificate(id));
    certificateRepository.delete(certificate);
    log.info("Certificate {} deleted", id);
  }

  private void resolveReferences(
      final CertificateDTO certificateDTO, final Certificate certificate) {
    final User issuedByEngineer =
        certificateDTO.getIssuedByEngineer() == null
            ? null
            : userRepository
                .findById(certificateDTO.getIssuedByEngineer())
                .orElseThrow(() -> new NotFoundException("issuedByEngineer not found"));
    certificate.setIssuedByEngineer(issuedByEngineer);
    final Job job =
        certificateDTO.getJob() == null
            ? null
            : jobRepository
                .findById(certificateDTO.getJob())
                .orElseThrow(() -> new NotFoundException("job not found"));
    certificate.setJob(job);
    final Property property =
        certificateDTO.getProperty() == null
            ? null
            : propertyRepository
                .findById(certificateDTO.getProperty())
                .orElseThrow(() -> new NotFoundException("property not found"));
    certificate.setProperty(property);
    final Certificate supersededBy =
        certificateDTO.getSupersededBy() == null
            ? null
            : certificateRepository
                .findById(certificateDTO.getSupersededBy())
                .orElseThrow(() -> new NotFoundException("supersededBy not found"));
    certificate.setSupersededBy(supersededBy);
  }

  @EventListener(BeforeDeleteUser.class)
  public void on(final BeforeDeleteUser event) {
    final ReferencedException referencedException = new ReferencedException();
    final Certificate issuedByEngineerCertificate =
        certificateRepository.findFirstByIssuedByEngineerId(event.getId());
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
    final Certificate propertyCertificate =
        certificateRepository.findFirstByPropertyId(event.getId());
    if (propertyCertificate != null) {
      referencedException.setKey("property.certificate.property.referenced");
      referencedException.addParam(propertyCertificate.getId());
      throw referencedException;
    }
  }

  @EventListener(BeforeDeleteCertificate.class)
  public void on(final BeforeDeleteCertificate event) {
    final ReferencedException referencedException = new ReferencedException();
    final Certificate supersededByCertificate =
        certificateRepository.findFirstBySupersededByIdAndIdNot(event.getId(), event.getId());
    if (supersededByCertificate != null) {
      referencedException.setKey("certificate.certificate.supersededBy.referenced");
      referencedException.addParam(supersededByCertificate.getId());
      throw referencedException;
    }
  }
}
