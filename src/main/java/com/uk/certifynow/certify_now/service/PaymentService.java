package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Payment;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteJob;
import com.uk.certifynow.certify_now.events.BeforeDeletePayment;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.PaymentDTO;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.PaymentRepository;
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
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final UserRepository userRepository;
  private final JobRepository jobRepository;
  private final ApplicationEventPublisher publisher;

  public PaymentService(
      final PaymentRepository paymentRepository,
      final UserRepository userRepository,
      final JobRepository jobRepository,
      final ApplicationEventPublisher publisher) {
    this.paymentRepository = paymentRepository;
    this.userRepository = userRepository;
    this.jobRepository = jobRepository;
    this.publisher = publisher;
  }

  public List<PaymentDTO> findAll() {
    final List<Payment> payments = paymentRepository.findAll(Sort.by("id"));
    return payments.stream().map(payment -> mapToDTO(payment, new PaymentDTO())).toList();
  }

  public PaymentDTO get(final UUID id) {
    return paymentRepository
        .findById(id)
        .map(payment -> mapToDTO(payment, new PaymentDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public UUID create(final PaymentDTO paymentDTO) {
    final Payment payment = new Payment();
    mapToEntity(paymentDTO, payment);
    return paymentRepository.save(payment).getId();
  }

  public void update(final UUID id, final PaymentDTO paymentDTO) {
    final Payment payment = paymentRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(paymentDTO, payment);
    paymentRepository.save(payment);
  }

  public void delete(final UUID id) {
    final Payment payment = paymentRepository.findById(id).orElseThrow(NotFoundException::new);
    publisher.publishEvent(new BeforeDeletePayment(id));
    paymentRepository.delete(payment);
  }

  private PaymentDTO mapToDTO(final Payment payment, final PaymentDTO paymentDTO) {
    paymentDTO.setId(payment.getId());
    paymentDTO.setAmountPence(payment.getAmountPence());
    paymentDTO.setCurrency(payment.getCurrency());
    paymentDTO.setRefundAmountPence(payment.getRefundAmountPence());
    paymentDTO.setRequiresAction(payment.getRequiresAction());
    paymentDTO.setAuthorisedAt(payment.getAuthorisedAt());
    paymentDTO.setCapturedAt(payment.getCapturedAt());
    paymentDTO.setCreatedAt(payment.getCreatedAt());
    paymentDTO.setRefundedAt(payment.getRefundedAt());
    paymentDTO.setUpdatedAt(payment.getUpdatedAt());
    paymentDTO.setFailureCode(payment.getFailureCode());
    paymentDTO.setStripeReceiptUrl(payment.getStripeReceiptUrl());
    paymentDTO.setThreeDsUrl(payment.getThreeDsUrl());
    paymentDTO.setFailureMessage(payment.getFailureMessage());
    paymentDTO.setRefundReason(payment.getRefundReason());
    paymentDTO.setStatus(payment.getStatus());
    paymentDTO.setStripeChargeId(payment.getStripeChargeId());
    paymentDTO.setStripeClientSecret(payment.getStripeClientSecret());
    paymentDTO.setStripePaymentIntentId(payment.getStripePaymentIntentId());
    paymentDTO.setCustomer(payment.getCustomer() == null ? null : payment.getCustomer().getId());
    paymentDTO.setJob(payment.getJob() == null ? null : payment.getJob().getId());
    return paymentDTO;
  }

  private Payment mapToEntity(final PaymentDTO paymentDTO, final Payment payment) {
    payment.setAmountPence(paymentDTO.getAmountPence());
    payment.setCurrency(paymentDTO.getCurrency());
    payment.setRefundAmountPence(paymentDTO.getRefundAmountPence());
    payment.setRequiresAction(paymentDTO.getRequiresAction());
    payment.setAuthorisedAt(paymentDTO.getAuthorisedAt());
    payment.setCapturedAt(paymentDTO.getCapturedAt());
    payment.setCreatedAt(paymentDTO.getCreatedAt());
    payment.setRefundedAt(paymentDTO.getRefundedAt());
    payment.setUpdatedAt(paymentDTO.getUpdatedAt());
    payment.setFailureCode(paymentDTO.getFailureCode());
    payment.setStripeReceiptUrl(paymentDTO.getStripeReceiptUrl());
    payment.setThreeDsUrl(paymentDTO.getThreeDsUrl());
    payment.setFailureMessage(paymentDTO.getFailureMessage());
    payment.setRefundReason(paymentDTO.getRefundReason());
    payment.setStatus(paymentDTO.getStatus());
    payment.setStripeChargeId(paymentDTO.getStripeChargeId());
    payment.setStripeClientSecret(paymentDTO.getStripeClientSecret());
    payment.setStripePaymentIntentId(paymentDTO.getStripePaymentIntentId());
    final User customer =
        paymentDTO.getCustomer() == null
            ? null
            : userRepository
                .findById(paymentDTO.getCustomer())
                .orElseThrow(() -> new NotFoundException("customer not found"));
    payment.setCustomer(customer);
    final Job job =
        paymentDTO.getJob() == null
            ? null
            : jobRepository
                .findById(paymentDTO.getJob())
                .orElseThrow(() -> new NotFoundException("job not found"));
    payment.setJob(job);
    return payment;
  }

  @EventListener(BeforeDeleteUser.class)
  public void on(final BeforeDeleteUser event) {
    final ReferencedException referencedException = new ReferencedException();
    final Payment customerPayment = paymentRepository.findFirstByCustomerId(event.getId());
    if (customerPayment != null) {
      referencedException.setKey("user.payment.customer.referenced");
      referencedException.addParam(customerPayment.getId());
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
