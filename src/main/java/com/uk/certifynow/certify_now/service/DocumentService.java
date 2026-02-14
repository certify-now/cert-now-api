package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Document;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.DocumentDTO;
import com.uk.certifynow.certify_now.repos.DocumentRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;


@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    public DocumentService(final DocumentRepository documentRepository,
            final UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
    }

    public List<DocumentDTO> findAll() {
        final List<Document> documents = documentRepository.findAll(Sort.by("id"));
        return documents.stream()
                .map(document -> mapToDTO(document, new DocumentDTO()))
                .toList();
    }

    public DocumentDTO get(final UUID id) {
        return documentRepository.findById(id)
                .map(document -> mapToDTO(document, new DocumentDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final DocumentDTO documentDTO) {
        final Document document = new Document();
        mapToEntity(documentDTO, document);
        return documentRepository.save(document).getId();
    }

    public void update(final UUID id, final DocumentDTO documentDTO) {
        final Document document = documentRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(documentDTO, document);
        documentRepository.save(document);
    }

    public void delete(final UUID id) {
        final Document document = documentRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        documentRepository.delete(document);
    }

    private DocumentDTO mapToDTO(final Document document, final DocumentDTO documentDTO) {
        documentDTO.setId(document.getId());
        documentDTO.setIsVirusScanned(document.getIsVirusScanned());
        documentDTO.setVirusScanClean(document.getVirusScanClean());
        documentDTO.setCreatedAt(document.getCreatedAt());
        documentDTO.setFileSizeBytes(document.getFileSizeBytes());
        documentDTO.setRelatedId(document.getRelatedId());
        documentDTO.setRelatedEntity(document.getRelatedEntity());
        documentDTO.setMimeType(document.getMimeType());
        documentDTO.setS3Bucket(document.getS3Bucket());
        documentDTO.setS3Key(document.getS3Key());
        documentDTO.setDocumentType(document.getDocumentType());
        documentDTO.setFileName(document.getFileName());
        documentDTO.setOwner(document.getOwner() == null ? null : document.getOwner().getId());
        return documentDTO;
    }

    private Document mapToEntity(final DocumentDTO documentDTO, final Document document) {
        document.setIsVirusScanned(documentDTO.getIsVirusScanned());
        document.setVirusScanClean(documentDTO.getVirusScanClean());
        document.setCreatedAt(documentDTO.getCreatedAt());
        document.setFileSizeBytes(documentDTO.getFileSizeBytes());
        document.setRelatedId(documentDTO.getRelatedId());
        document.setRelatedEntity(documentDTO.getRelatedEntity());
        document.setMimeType(documentDTO.getMimeType());
        document.setS3Bucket(documentDTO.getS3Bucket());
        document.setS3Key(documentDTO.getS3Key());
        document.setDocumentType(documentDTO.getDocumentType());
        document.setFileName(documentDTO.getFileName());
        final User owner = documentDTO.getOwner() == null ? null : userRepository.findById(documentDTO.getOwner())
                .orElseThrow(() -> new NotFoundException("owner not found"));
        document.setOwner(owner);
        return document;
    }

    @EventListener(BeforeDeleteUser.class)
    public void on(final BeforeDeleteUser event) {
        final ReferencedException referencedException = new ReferencedException();
        final Document ownerDocument = documentRepository.findFirstByOwnerId(event.getId());
        if (ownerDocument != null) {
            referencedException.setKey("user.document.owner.referenced");
            referencedException.addParam(ownerDocument.getId());
            throw referencedException;
        }
    }

}
