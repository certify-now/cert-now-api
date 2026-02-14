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
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;


@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;

    public ReviewService(final ReviewRepository reviewRepository, final JobRepository jobRepository,
            final UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
    }

    public List<ReviewDTO> findAll() {
        final List<Review> reviews = reviewRepository.findAll(Sort.by("id"));
        return reviews.stream()
                .map(review -> mapToDTO(review, new ReviewDTO()))
                .toList();
    }

    public ReviewDTO get(final UUID id) {
        return reviewRepository.findById(id)
                .map(review -> mapToDTO(review, new ReviewDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final ReviewDTO reviewDTO) {
        final Review review = new Review();
        mapToEntity(reviewDTO, review);
        return reviewRepository.save(review).getId();
    }

    public void update(final UUID id, final ReviewDTO reviewDTO) {
        final Review review = reviewRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(reviewDTO, review);
        reviewRepository.save(review);
    }

    public void delete(final UUID id) {
        final Review review = reviewRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        reviewRepository.delete(review);
    }

    private ReviewDTO mapToDTO(final Review review, final ReviewDTO reviewDTO) {
        reviewDTO.setId(review.getId());
        reviewDTO.setCommunication(review.getCommunication());
        reviewDTO.setIsVisible(review.getIsVisible());
        reviewDTO.setProfessionalism(review.getProfessionalism());
        reviewDTO.setPunctuality(review.getPunctuality());
        reviewDTO.setQuality(review.getQuality());
        reviewDTO.setRating(review.getRating());
        reviewDTO.setCreatedAt(review.getCreatedAt());
        reviewDTO.setComment(review.getComment());
        reviewDTO.setDirection(review.getDirection());
        reviewDTO.setJob(review.getJob() == null ? null : review.getJob().getId());
        reviewDTO.setReviewee(review.getReviewee() == null ? null : review.getReviewee().getId());
        reviewDTO.setReviewer(review.getReviewer() == null ? null : review.getReviewer().getId());
        return reviewDTO;
    }

    private Review mapToEntity(final ReviewDTO reviewDTO, final Review review) {
        review.setCommunication(reviewDTO.getCommunication());
        review.setIsVisible(reviewDTO.getIsVisible());
        review.setProfessionalism(reviewDTO.getProfessionalism());
        review.setPunctuality(reviewDTO.getPunctuality());
        review.setQuality(reviewDTO.getQuality());
        review.setRating(reviewDTO.getRating());
        review.setCreatedAt(reviewDTO.getCreatedAt());
        review.setComment(reviewDTO.getComment());
        review.setDirection(reviewDTO.getDirection());
        final Job job = reviewDTO.getJob() == null ? null : jobRepository.findById(reviewDTO.getJob())
                .orElseThrow(() -> new NotFoundException("job not found"));
        review.setJob(job);
        final User reviewee = reviewDTO.getReviewee() == null ? null : userRepository.findById(reviewDTO.getReviewee())
                .orElseThrow(() -> new NotFoundException("reviewee not found"));
        review.setReviewee(reviewee);
        final User reviewer = reviewDTO.getReviewer() == null ? null : userRepository.findById(reviewDTO.getReviewer())
                .orElseThrow(() -> new NotFoundException("reviewer not found"));
        review.setReviewer(reviewer);
        return review;
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
