package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.RefreshToken;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;


public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    RefreshToken findFirstByUserId(UUID id);

}
