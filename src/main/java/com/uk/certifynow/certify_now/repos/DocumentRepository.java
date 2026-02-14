package com.uk.certifynow.certify_now.repos;

import com.uk.certifynow.certify_now.domain.Document;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;


public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Document findFirstByOwnerId(UUID id);

}
