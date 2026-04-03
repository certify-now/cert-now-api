package com.uk.certifynow.certify_now.service.payment;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Payment;
import com.uk.certifynow.certify_now.domain.Payout;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteJob;
import com.uk.certifynow.certify_now.events.BeforeDeletePayment;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.PayoutDTO;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.PaymentRepository;
import com.uk.certifynow.certify_now.repos.PayoutRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class PayoutService {

  private final PayoutRepository payoutRepository;
  private final UserRepository userRepository;
  private final JobRepository jobRepository;
  private final PaymentRepository paymentRepository;

  public PayoutService(
      final PayoutRepository payoutRepository,
      final UserRepository userRepository,
      final JobRepository jobRepository,
      final PaymentRepository paymentRepository) {
    this.payoutRepository = payoutRepository;
    this.userRepository = userRepository;
    this.jobRepository = jobRepository;
    this.paymentRepository = paymentRepository;
  }

  public List<PayoutDTO> findAll() {
    final List<Payout> payouts = payoutRepository.findAll(Sort.by("id"));
    return payouts.stream().map(payout -> mapToDTO(payout, new PayoutDTO())).toList();
  }

  public PayoutDTO get(final UUID id) {
    return payoutRepository
        .findById(id)
        .map(payout -> mapToDTO(payout, new PayoutDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public UUID create(final PayoutDTO payoutDTO) {
    final Payout payout = new Payout();
    mapToEntity(payoutDTO, payout);
    return payoutRepository.save(payout).getId();
  }

  public void update(final UUID id, final PayoutDTO payoutDTO) {
    final Payout payout = payoutRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(payoutDTO, payout);
    payoutRepository.save(payout);
  }

  public void delete(final UUID id) {
    final Payout payout = payoutRepository.findById(id).orElseThrow(NotFoundException::new);
    payoutRepository.delete(payout);
  }

  private PayoutDTO mapToDTO(final Payout payout, final PayoutDTO payoutDTO) {
    payoutDTO.setId(payout.getId());
    payoutDTO.setAmountPence(payout.getAmountPence());
    payoutDTO.setCommissionPence(payout.getCommissionPence());
    payoutDTO.setCurrency(payout.getCurrency());
    payoutDTO.setInstantFeePence(payout.getInstantFeePence());
    payoutDTO.setIsInstant(payout.getIsInstant());
    payoutDTO.setNetPence(payout.getNetPence());
    payoutDTO.setScheduledFor(payout.getScheduledFor());
    payoutDTO.setCompletedAt(payout.getCompletedAt());
    payoutDTO.setCreatedAt(payout.getCreatedAt());
    payoutDTO.setUpdatedAt(payout.getUpdatedAt());
    payoutDTO.setFailureReason(payout.getFailureReason());
    payoutDTO.setStatus(payout.getStatus());
    payoutDTO.setStripePayoutId(payout.getStripePayoutId());
    payoutDTO.setStripeTransferId(payout.getStripeTransferId());
    payoutDTO.setEngineer(payout.getEngineer() == null ? null : payout.getEngineer().getId());
    payoutDTO.setJob(payout.getJob() == null ? null : payout.getJob().getId());
    payoutDTO.setPayment(payout.getPayment() == null ? null : payout.getPayment().getId());
    return payoutDTO;
  }

  private Payout mapToEntity(final PayoutDTO payoutDTO, final Payout payout) {
    payout.setAmountPence(payoutDTO.getAmountPence());
    payout.setCommissionPence(payoutDTO.getCommissionPence());
    payout.setCurrency(payoutDTO.getCurrency());
    payout.setInstantFeePence(payoutDTO.getInstantFeePence());
    payout.setIsInstant(payoutDTO.getIsInstant());
    payout.setNetPence(payoutDTO.getNetPence());
    payout.setScheduledFor(payoutDTO.getScheduledFor());
    payout.setCompletedAt(payoutDTO.getCompletedAt());
    payout.setCreatedAt(payoutDTO.getCreatedAt());
    payout.setUpdatedAt(payoutDTO.getUpdatedAt());
    payout.setFailureReason(payoutDTO.getFailureReason());
    payout.setStatus(payoutDTO.getStatus());
    payout.setStripePayoutId(payoutDTO.getStripePayoutId());
    payout.setStripeTransferId(payoutDTO.getStripeTransferId());
    final User engineer =
        payoutDTO.getEngineer() == null
            ? null
            : userRepository
                .findById(payoutDTO.getEngineer())
                .orElseThrow(() -> new NotFoundException("engineer not found"));
    payout.setEngineer(engineer);
    final Job job =
        payoutDTO.getJob() == null
            ? null
            : jobRepository
                .findById(payoutDTO.getJob())
                .orElseThrow(() -> new NotFoundException("job not found"));
    payout.setJob(job);
    final Payment payment =
        payoutDTO.getPayment() == null
            ? null
            : paymentRepository
                .findById(payoutDTO.getPayment())
                .orElseThrow(() -> new NotFoundException("payment not found"));
    payout.setPayment(payment);
    return payout;
  }

  @EventListener(BeforeDeleteUser.class)
  public void on(final BeforeDeleteUser event) {
    final ReferencedException referencedException = new ReferencedException();
    final Payout engineerPayout = payoutRepository.findFirstByEngineerId(event.getId());
    if (engineerPayout != null) {
      referencedException.setKey("user.payout.engineer.referenced");
      referencedException.addParam(engineerPayout.getId());
      throw referencedException;
    }
  }

  @EventListener(BeforeDeleteJob.class)
  public void on(final BeforeDeleteJob event) {
    final ReferencedException referencedException = new ReferencedException();
    final Payout jobPayout = payoutRepository.findFirstByJobId(event.getId());
    if (jobPayout != null) {
      referencedException.setKey("job.payout.job.referenced");
      referencedException.addParam(jobPayout.getId());
      throw referencedException;
    }
  }

  @EventListener(BeforeDeletePayment.class)
  public void on(final BeforeDeletePayment event) {
    final ReferencedException referencedException = new ReferencedException();
    final Payout paymentPayout = payoutRepository.findFirstByPaymentId(event.getId());
    if (paymentPayout != null) {
      referencedException.setKey("payment.payout.payment.referenced");
      referencedException.addParam(paymentPayout.getId());
      throw referencedException;
    }
  }
}
