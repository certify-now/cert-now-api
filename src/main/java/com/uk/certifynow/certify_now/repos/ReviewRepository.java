package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.Review;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

  Review findFirstByJobId(UUID id);

  Review findFirstByRevieweeId(UUID id);

  Review findFirstByReviewerId(UUID id);
}
