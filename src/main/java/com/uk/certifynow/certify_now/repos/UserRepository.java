package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;


public interface UserRepository extends JpaRepository<User, UUID> {
}
