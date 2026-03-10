package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Payment;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteJob;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.PaymentDTO;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.PaymentRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.mappers.PaymentMapper;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final UserRepository userRepository;
  private final JobRepository jobRepository;
  private final PaymentMapper paymentMapper;

  public PaymentService(
      final PaymentRepository paymentRepository,
      final UserRepository userRepository,
      final JobRepository jobRepository,
      final PaymentMapper paymentMapper) {
    this.paymentRepository = paymentRepository;
    this.userRepository = userRepository;
    this.jobRepository = jobRepository;
    this.paymentMapper = paymentMapper;
  }

  public List<PaymentDTO> findAll() {
    final List<Payment> payments = paymentRepository.findAll(Sort.by("id"));
    return payments.stream().map(paymentMapper::toDTO).toList();
  }

  public PaymentDTO get(final UUID id) {
    return paymentRepository
        .findById(id)
        .map(paymentMapper::toDTO)
        .orElseThrow(NotFoundException::new);
  }

  @Transactional
  public UUID create(final PaymentDTO paymentDTO) {
    final Payment payment = new Payment();
    paymentMapper.updateEntity(paymentDTO, payment);
    resolveReferences(paymentDTO, payment);
    UUID savedId = paymentRepository.save(payment).getId();
    log.info("Payment {} created (amount={})", savedId, paymentDTO.getAmountPence());
    return savedId;
  }

  @Transactional
  public void update(final UUID id, final PaymentDTO paymentDTO) {
    final Payment payment = paymentRepository.findById(id).orElseThrow(NotFoundException::new);
    paymentMapper.updateEntity(paymentDTO, payment);
    resolveReferences(paymentDTO, payment);
    paymentRepository.save(payment);
    log.info("Payment {} updated", id);
  }

  @Transactional
  public void delete(final UUID id) {
    paymentRepository.findById(id).orElseThrow(NotFoundException::new);
    paymentRepository.deleteById(id);
    log.info("Payment {} deleted", id);
  }

  private void resolveReferences(final PaymentDTO paymentDTO, final Payment payment) {
    final User user =
        paymentDTO.getUser() == null
            ? null
            : userRepository
                .findById(paymentDTO.getUser())
                .orElseThrow(() -> new NotFoundException("user not found"));
    payment.setUser(user);
    final Job job =
        paymentDTO.getJob() == null
            ? null
            : jobRepository
                .findById(paymentDTO.getJob())
                .orElseThrow(() -> new NotFoundException("job not found"));
    payment.setJob(job);
  }

  @EventListener(BeforeDeleteUser.class)
  public void on(final BeforeDeleteUser event) {
    final ReferencedException referencedException = new ReferencedException();
    final Payment userPayment = paymentRepository.findFirstByUserId(event.getId());
    if (userPayment != null) {
      referencedException.setKey("user.payment.user.referenced");
      referencedException.addParam(userPayment.getId());
      throw referencedException;
    }
  }

  @EventListener(BeforeDeleteJob.class)
  public void on(final BeforeDeleteJob event) {
    final ReferencedException referencedException = new ReferencedException();
    final Payment jobPayment = paymentRepository.findFirstByJobId(event.getId());
    if (jobPayment != null) {
      referencedException.setKey("job.payment.job.referenced");
      referencedException.addParam(jobPayment.getId());
      throw referencedException;
    }
  }
}
