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
    UUID savedId = reviewRepository.save(review).getId();
    log.info("Review {} created (rating={})", savedId, reviewDTO.getRating());
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
    reviewRepository.findById(id).orElseThrow(NotFoundException::new);
    reviewRepository.deleteById(id);
    log.info("Review {} deleted", id);
  }

  private void resolveReferences(final ReviewDTO reviewDTO, final Review review) {
    final Job job =
        reviewDTO.getJob() == null
            ? null
            : jobRepository
                .findById(reviewDTO.getJob())
                .orElseThrow(() -> new NotFoundException("job not found"));
    review.setJob(job);
    final User reviewer =
        reviewDTO.getReviewer() == null
            ? null
            : userRepository
                .findById(reviewDTO.getReviewer())
                .orElseThrow(() -> new NotFoundException("reviewer not found"));
    review.setReviewer(reviewer);
    final User reviewee =
        reviewDTO.getReviewee() == null
            ? null
            : userRepository
                .findById(reviewDTO.getReviewee())
                .orElseThrow(() -> new NotFoundException("reviewee not found"));
    review.setReviewee(reviewee);
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
  public void onReviewer(final BeforeDeleteUser event) {
    final ReferencedException referencedException = new ReferencedException();
    final Review reviewerReview = reviewRepository.findFirstByReviewerId(event.getId());
    if (reviewerReview != null) {
      referencedException.setKey("user.review.reviewer.referenced");
      referencedException.addParam(reviewerReview.getId());
      throw referencedException;
    }
  }
}
