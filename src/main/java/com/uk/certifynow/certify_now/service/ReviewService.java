package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Review;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteJob;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.ReviewDTO;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.ReviewRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.mappers.ReviewMapper;
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
public class ReviewService {

  private final ReviewRepository reviewRepository;
  private final JobRepository jobRepository;
  private final UserRepository userRepository;
  private final ReviewMapper reviewMapper;

  public ReviewService(
      final ReviewRepository reviewRepository,
      final JobRepository jobRepository,
      final UserRepository userRepository,
      final ReviewMapper reviewMapper) {
    this.reviewRepository = reviewRepository;
    this.jobRepository = jobRepository;
    this.userRepository = userRepository;
    this.reviewMapper = reviewMapper;
  }

  public List<ReviewDTO> findAll() {
    final List<Review> reviews = reviewRepository.findAll(Sort.by("id"));
    return reviews.stream().map(reviewMapper::toDTO).toList();
  }

  public ReviewDTO get(final UUID id) {
    return reviewRepository
        .findById(id)
        .map(reviewMapper::toDTO)
        .orElseThrow(NotFoundException::new);
  }

  @Transactional
  public UUID create(final ReviewDTO reviewDTO) {
    final Review review = new Review();
    reviewMapper.updateEntity(reviewDTO, review);
    resolveReferences(reviewDTO, review);
    final UUID savedId = reviewRepository.save(review).getId();
    log.info("Review {} created", savedId);
    return savedId;
  }

  @Transactional
  public void update(final UUID id, final ReviewDTO reviewDTO) {
    final Review review = reviewRepository.findById(id).orElseThrow(NotFoundException::new);
    reviewMapper.updateEntity(reviewDTO, review);
    resolveReferences(reviewDTO, review);
    reviewRepository.save(review);
    log.info("Review {} updated", id);
  }

  @Transactional
  public void delete(final UUID id) {
    final Review review = reviewRepository.findById(id).orElseThrow(NotFoundException::new);
    reviewRepository.delete(review);
    log.info("Review {} deleted", id);
  }

  private void resolveReferences(final ReviewDTO dto, final Review entity) {
    final Job job =
        dto.getJob() == null
            ? null
            : jobRepository
                .findById(dto.getJob())
                .orElseThrow(() -> new NotFoundException("job not found"));
    entity.setJob(job);
    final User reviewee =
        dto.getReviewee() == null
            ? null
            : userRepository
                .findById(dto.getReviewee())
                .orElseThrow(() -> new NotFoundException("reviewee not found"));
    entity.setReviewee(reviewee);
    final User reviewer =
        dto.getReviewer() == null
            ? null
            : userRepository
                .findById(dto.getReviewer())
                .orElseThrow(() -> new NotFoundException("reviewer not found"));
    entity.setReviewer(reviewer);
  }

  @EventListener(BeforeDeleteJob.class)
  public void on(final BeforeDeleteJob event) {
    final ReferencedException referencedException = new ReferencedException();
    final Review jobReview = reviewRepository.findFirstByJobId(event.getId());
    if (jobReview != null) {
      referencedException.setKey("job.review.job.referenced");
      referencedException.addParam(jobReview.getId());
      throw referencedException;
    }
  }

  @EventListener(BeforeDeleteUser.class)
  public void on(final BeforeDeleteUser event) {
    final ReferencedException referencedException = new ReferencedException();
    final Review revieweeReview = reviewRepository.findFirstByRevieweeId(event.getId());
    if (revieweeReview != null) {
      referencedException.setKey("user.review.reviewee.referenced");
      referencedException.addParam(revieweeReview.getId());
      throw referencedException;
    }
    final Review reviewerReview = reviewRepository.findFirstByReviewerId(event.getId());
    if (reviewerReview != null) {
      referencedException.setKey("user.review.reviewer.referenced");
      referencedException.addParam(reviewerReview.getId());
      throw referencedException;
    }
  }
}
