package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.GasApplianceInspection;
import com.uk.certifynow.certify_now.domain.GasSafetyInspection;
import com.uk.certifynow.certify_now.events.BeforeDeleteGasSafetyInspection;
import com.uk.certifynow.certify_now.model.GasApplianceInspectionDTO;
import com.uk.certifynow.certify_now.repos.GasApplianceInspectionRepository;
import com.uk.certifynow.certify_now.repos.GasSafetyInspectionRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;


@Service
public class GasApplianceInspectionService {

    private final GasApplianceInspectionRepository gasApplianceInspectionRepository;
    private final GasSafetyInspectionRepository gasSafetyInspectionRepository;

    public GasApplianceInspectionService(
            final GasApplianceInspectionRepository gasApplianceInspectionRepository,
            final GasSafetyInspectionRepository gasSafetyInspectionRepository) {
        this.gasApplianceInspectionRepository = gasApplianceInspectionRepository;
        this.gasSafetyInspectionRepository = gasSafetyInspectionRepository;
    }

    public List<GasApplianceInspectionDTO> findAll() {
        final List<GasApplianceInspection> gasApplianceInspections = gasApplianceInspectionRepository.findAll(Sort.by("id"));
        return gasApplianceInspections.stream()
                .map(gasApplianceInspection -> mapToDTO(gasApplianceInspection, new GasApplianceInspectionDTO()))
                .toList();
    }

    public GasApplianceInspectionDTO get(final UUID id) {
        return gasApplianceInspectionRepository.findById(id)
                .map(gasApplianceInspection -> mapToDTO(gasApplianceInspection, new GasApplianceInspectionDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final GasApplianceInspectionDTO gasApplianceInspectionDTO) {
        final GasApplianceInspection gasApplianceInspection = new GasApplianceInspection();
        mapToEntity(gasApplianceInspectionDTO, gasApplianceInspection);
        return gasApplianceInspectionRepository.save(gasApplianceInspection).getId();
    }

    public void update(final UUID id, final GasApplianceInspectionDTO gasApplianceInspectionDTO) {
        final GasApplianceInspection gasApplianceInspection = gasApplianceInspectionRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(gasApplianceInspectionDTO, gasApplianceInspection);
        gasApplianceInspectionRepository.save(gasApplianceInspection);
    }

    public void delete(final UUID id) {
        final GasApplianceInspection gasApplianceInspection = gasApplianceInspectionRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        gasApplianceInspectionRepository.delete(gasApplianceInspection);
    }

    private GasApplianceInspectionDTO mapToDTO(final GasApplianceInspection gasApplianceInspection,
            final GasApplianceInspectionDTO gasApplianceInspectionDTO) {
        gasApplianceInspectionDTO.setId(gasApplianceInspection.getId());
        gasApplianceInspectionDTO.setApplianceOrder(gasApplianceInspection.getApplianceOrder());
        gasApplianceInspectionDTO.setCo2ReadingPercent(gasApplianceInspection.getCo2ReadingPercent());
        gasApplianceInspectionDTO.setCoReadingPercent(gasApplianceInspection.getCoReadingPercent());
        gasApplianceInspectionDTO.setFlamePicturePass(gasApplianceInspection.getFlamePicturePass());
        gasApplianceInspectionDTO.setOperatingPressureMbar(gasApplianceInspection.getOperatingPressureMbar());
        gasApplianceInspectionDTO.setRatio(gasApplianceInspection.getRatio());
        gasApplianceInspectionDTO.setSafetyDeviceCorrect(gasApplianceInspection.getSafetyDeviceCorrect());
        gasApplianceInspectionDTO.setSpillageTestPass(gasApplianceInspection.getSpillageTestPass());
        gasApplianceInspectionDTO.setVentilationPass(gasApplianceInspection.getVentilationPass());
        gasApplianceInspectionDTO.setCreatedAt(gasApplianceInspection.getCreatedAt());
        gasApplianceInspectionDTO.setApplianceType(gasApplianceInspection.getApplianceType());
        gasApplianceInspectionDTO.setFlueType(gasApplianceInspection.getFlueType());
        gasApplianceInspectionDTO.setGcNumber(gasApplianceInspection.getGcNumber());
        gasApplianceInspectionDTO.setLocationInProperty(gasApplianceInspection.getLocationInProperty());
        gasApplianceInspectionDTO.setMake(gasApplianceInspection.getMake());
        gasApplianceInspectionDTO.setModel(gasApplianceInspection.getModel());
        gasApplianceInspectionDTO.setDefectSeverity(gasApplianceInspection.getDefectSeverity());
        gasApplianceInspectionDTO.setDefectsIdentified(gasApplianceInspection.getDefectsIdentified());
        gasApplianceInspectionDTO.setResult(gasApplianceInspection.getResult());
        gasApplianceInspectionDTO.setGasInspection(gasApplianceInspection.getGasInspection() == null ? null : gasApplianceInspection.getGasInspection().getId());
        return gasApplianceInspectionDTO;
    }

    private GasApplianceInspection mapToEntity(
            final GasApplianceInspectionDTO gasApplianceInspectionDTO,
            final GasApplianceInspection gasApplianceInspection) {
        gasApplianceInspection.setApplianceOrder(gasApplianceInspectionDTO.getApplianceOrder());
        gasApplianceInspection.setCo2ReadingPercent(gasApplianceInspectionDTO.getCo2ReadingPercent());
        gasApplianceInspection.setCoReadingPercent(gasApplianceInspectionDTO.getCoReadingPercent());
        gasApplianceInspection.setFlamePicturePass(gasApplianceInspectionDTO.getFlamePicturePass());
        gasApplianceInspection.setOperatingPressureMbar(gasApplianceInspectionDTO.getOperatingPressureMbar());
        gasApplianceInspection.setRatio(gasApplianceInspectionDTO.getRatio());
        gasApplianceInspection.setSafetyDeviceCorrect(gasApplianceInspectionDTO.getSafetyDeviceCorrect());
        gasApplianceInspection.setSpillageTestPass(gasApplianceInspectionDTO.getSpillageTestPass());
        gasApplianceInspection.setVentilationPass(gasApplianceInspectionDTO.getVentilationPass());
        gasApplianceInspection.setCreatedAt(gasApplianceInspectionDTO.getCreatedAt());
        gasApplianceInspection.setApplianceType(gasApplianceInspectionDTO.getApplianceType());
        gasApplianceInspection.setFlueType(gasApplianceInspectionDTO.getFlueType());
        gasApplianceInspection.setGcNumber(gasApplianceInspectionDTO.getGcNumber());
        gasApplianceInspection.setLocationInProperty(gasApplianceInspectionDTO.getLocationInProperty());
        gasApplianceInspection.setMake(gasApplianceInspectionDTO.getMake());
        gasApplianceInspection.setModel(gasApplianceInspectionDTO.getModel());
        gasApplianceInspection.setDefectSeverity(gasApplianceInspectionDTO.getDefectSeverity());
        gasApplianceInspection.setDefectsIdentified(gasApplianceInspectionDTO.getDefectsIdentified());
        gasApplianceInspection.setResult(gasApplianceInspectionDTO.getResult());
        final GasSafetyInspection gasInspection = gasApplianceInspectionDTO.getGasInspection() == null ? null : gasSafetyInspectionRepository.findById(gasApplianceInspectionDTO.getGasInspection())
                .orElseThrow(() -> new NotFoundException("gasInspection not found"));
        gasApplianceInspection.setGasInspection(gasInspection);
        return gasApplianceInspection;
    }

    @EventListener(BeforeDeleteGasSafetyInspection.class)
    public void on(final BeforeDeleteGasSafetyInspection event) {
        final ReferencedException referencedException = new ReferencedException();
        final GasApplianceInspection gasInspectionGasApplianceInspection = gasApplianceInspectionRepository.findFirstByGasInspectionId(event.getId());
        if (gasInspectionGasApplianceInspection != null) {
            referencedException.setKey("gasSafetyInspection.gasApplianceInspection.gasInspection.referenced");
            referencedException.addParam(gasInspectionGasApplianceInspection.getId());
            throw referencedException;
        }
    }

}
