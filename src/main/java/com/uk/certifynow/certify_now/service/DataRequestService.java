package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.DataRequest;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.DataRequestDTO;
import com.uk.certifynow.certify_now.repos.DataRequestRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;


@Service
public class DataRequestService {

    private final DataRequestRepository dataRequestRepository;
    private final UserRepository userRepository;

    public DataRequestService(final DataRequestRepository dataRequestRepository,
            final UserRepository userRepository) {
        this.dataRequestRepository = dataRequestRepository;
        this.userRepository = userRepository;
    }

    public List<DataRequestDTO> findAll() {
        final List<DataRequest> dataRequests = dataRequestRepository.findAll(Sort.by("id"));
        return dataRequests.stream()
                .map(dataRequest -> mapToDTO(dataRequest, new DataRequestDTO()))
                .toList();
    }

    public DataRequestDTO get(final UUID id) {
        return dataRequestRepository.findById(id)
                .map(dataRequest -> mapToDTO(dataRequest, new DataRequestDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final DataRequestDTO dataRequestDTO) {
        final DataRequest dataRequest = new DataRequest();
        mapToEntity(dataRequestDTO, dataRequest);
        return dataRequestRepository.save(dataRequest).getId();
    }

    public void update(final UUID id, final DataRequestDTO dataRequestDTO) {
        final DataRequest dataRequest = dataRequestRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(dataRequestDTO, dataRequest);
        dataRequestRepository.save(dataRequest);
    }

    public void delete(final UUID id) {
        final DataRequest dataRequest = dataRequestRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        dataRequestRepository.delete(dataRequest);
    }

    private DataRequestDTO mapToDTO(final DataRequest dataRequest,
            final DataRequestDTO dataRequestDTO) {
        dataRequestDTO.setId(dataRequest.getId());
        dataRequestDTO.setCompletedAt(dataRequest.getCompletedAt());
        dataRequestDTO.setCreatedAt(dataRequest.getCreatedAt());
        dataRequestDTO.setDownloadExpiresAt(dataRequest.getDownloadExpiresAt());
        dataRequestDTO.setUpdatedAt(dataRequest.getUpdatedAt());
        dataRequestDTO.setRequestType(dataRequest.getRequestType());
        dataRequestDTO.setStatus(dataRequest.getStatus());
        dataRequestDTO.setDownloadUrl(dataRequest.getDownloadUrl());
        dataRequestDTO.setNotes(dataRequest.getNotes());
        dataRequestDTO.setUser(dataRequest.getUser() == null ? null : dataRequest.getUser().getId());
        return dataRequestDTO;
    }

    private DataRequest mapToEntity(final DataRequestDTO dataRequestDTO,
            final DataRequest dataRequest) {
        dataRequest.setCompletedAt(dataRequestDTO.getCompletedAt());
        dataRequest.setCreatedAt(dataRequestDTO.getCreatedAt());
        dataRequest.setDownloadExpiresAt(dataRequestDTO.getDownloadExpiresAt());
        dataRequest.setUpdatedAt(dataRequestDTO.getUpdatedAt());
        dataRequest.setRequestType(dataRequestDTO.getRequestType());
        dataRequest.setStatus(dataRequestDTO.getStatus());
        dataRequest.setDownloadUrl(dataRequestDTO.getDownloadUrl());
        dataRequest.setNotes(dataRequestDTO.getNotes());
        final User user = dataRequestDTO.getUser() == null ? null : userRepository.findById(dataRequestDTO.getUser())
                .orElseThrow(() -> new NotFoundException("user not found"));
        dataRequest.setUser(user);
        return dataRequest;
    }

    @EventListener(BeforeDeleteUser.class)
    public void on(final BeforeDeleteUser event) {
        final ReferencedException referencedException = new ReferencedException();
        final DataRequest userDataRequest = dataRequestRepository.findFirstByUserId(event.getId());
        if (userDataRequest != null) {
            referencedException.setKey("user.dataRequest.user.referenced");
            referencedException.addParam(userDataRequest.getId());
            throw referencedException;
        }
    }

}
