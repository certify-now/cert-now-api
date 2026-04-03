package com.uk.certifynow.certify_now.service.storage;

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

  public DocumentService(
      final DocumentRepository documentRepository, final UserRepository userRepository) {
    this.documentRepository = documentRepository;
    this.userRepository = userRepository;
  }

  public List<DocumentDTO> findAll() {
    final List<Document> documents = documentRepository.findAll(Sort.by("id"));
    return documents.stream().map(document -> mapToDTO(document, new DocumentDTO())).toList();
  }

  public DocumentDTO get(final UUID id) {
    return documentRepository
        .findById(id)
        .map(document -> mapToDTO(document, new DocumentDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public UUID create(final DocumentDTO documentDTO) {
    final Document document = new Document();
    mapToEntity(documentDTO, document);
    return documentRepository.save(document).getId();
  }

  public void update(final UUID id, final DocumentDTO documentDTO) {
    final Document document = documentRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(documentDTO, document);
    documentRepository.save(document);
  }

  public void delete(final UUID id) {
    final Document document = documentRepository.findById(id).orElseThrow(NotFoundException::new);
    documentRepository.delete(document);
  }

  private DocumentDTO mapToDTO(final Document document, final DocumentDTO documentDTO) {
    documentDTO.setId(document.getId());
    documentDTO.setStorageUrl(document.getStorageUrl());
    documentDTO.setFileName(document.getFileName());
    documentDTO.setMimeType(document.getMimeType());
    documentDTO.setFileSizeBytes(document.getFileSizeBytes());
    documentDTO.setIsVirusScanned(document.getIsVirusScanned());
    documentDTO.setVirusScanClean(document.getVirusScanClean());
    documentDTO.setUploadedBy(
        document.getUploadedBy() == null ? null : document.getUploadedBy().getId());
    documentDTO.setCreatedAt(document.getCreatedAt());
    documentDTO.setUpdatedAt(document.getUpdatedAt());
    return documentDTO;
  }

  private Document mapToEntity(final DocumentDTO documentDTO, final Document document) {
    document.setStorageUrl(documentDTO.getStorageUrl());
    document.setFileName(documentDTO.getFileName());
    document.setMimeType(documentDTO.getMimeType());
    document.setFileSizeBytes(documentDTO.getFileSizeBytes());
    document.setIsVirusScanned(
        documentDTO.getIsVirusScanned() != null ? documentDTO.getIsVirusScanned() : false);
    document.setVirusScanClean(documentDTO.getVirusScanClean());
    final User uploadedBy =
        documentDTO.getUploadedBy() == null
            ? null
            : userRepository
                .findById(documentDTO.getUploadedBy())
                .orElseThrow(() -> new NotFoundException("uploadedBy user not found"));
    document.setUploadedBy(uploadedBy);
    return document;
  }

  @EventListener(BeforeDeleteUser.class)
  public void on(final BeforeDeleteUser event) {
    final ReferencedException referencedException = new ReferencedException();
    final Document uploadedByDocument = documentRepository.findFirstByUploadedById(event.getId());
    if (uploadedByDocument != null) {
      referencedException.setKey("user.document.uploadedBy.referenced");
      referencedException.addParam(uploadedByDocument.getId());
      throw referencedException;
    }
  }
}
