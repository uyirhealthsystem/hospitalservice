package com.uyir.hospital.service.impl;

import com.uyir.hospital.catalog.EmergencyConditionCatalog;
import com.uyir.hospital.catalog.SpecialtyCatalog;
import com.uyir.hospital.dto.DoctorRequest;
import com.uyir.hospital.dto.DoctorResponse;
import com.uyir.hospital.dto.DoctorSearchRequest;
import com.uyir.hospital.dto.PageResponse;
import com.uyir.hospital.exception.DuplicateResourceException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.mapper.DoctorMapper;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.embedded.HospitalAssociation;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.DoctorSearchCriteria;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.service.DoctorService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DoctorServiceImpl implements DoctorService {

    private final DoctorRepository doctorRepository;
    private final HospitalRepository hospitalRepository;
    private final DoctorMapper doctorMapper;
    private final SpecialtyCatalog specialtyCatalog;
    private final EmergencyConditionCatalog emergencyConditionCatalog;

    @Override
    public DoctorResponse create(DoctorRequest request) {
        List<String> specialties = specialtyCatalog.normalize("specialties", request.getSpecialties());
        List<String> emergencyConditions = normalizedEmergencyConditions(request);
        if (doctorRepository.existsByTnmcNumber(request.getTnmcNumber())) {
            throw new DuplicateResourceException(
                    "Doctor with TNMC number '" + request.getTnmcNumber() + "' already exists");
        }

        Doctor doctor = doctorMapper.toEntity(request);
        doctor.setSpecialties(specialties);
        doctor.setEmergencyConditionsHandled(emergencyConditions);
        doctor.setActive(true);
        doctor.setCreatedAt(Instant.now());
        doctor.setUpdatedAt(Instant.now());

        return doctorMapper.toResponse(doctorRepository.save(doctor));
    }

    @Override
    public DoctorResponse getById(String id) {
        return doctorMapper.toResponse(findEntityOrThrow(id));
    }

    @Override
    public List<DoctorResponse> getAll() {
        return doctorRepository.findAll().stream().map(doctorMapper::toResponse).toList();
    }

    @Override
    public PageResponse<DoctorResponse> search(DoctorSearchRequest request, Pageable pageable) {
        // A code ("CAR001") searches by its official name. Anything not in the master list is searched as
        // typed, so doctors saved before the list existed can still be found.
        String specialty = request.getSpecialty() == null ? null
                : specialtyCatalog.resolve(request.getSpecialty()).orElse(request.getSpecialty());
        DoctorSearchCriteria criteria = DoctorSearchCriteria.builder()
                .specialty(specialty)
                .hospitalId(request.getHospitalId())
                .engagementType(request.getEngagementType())
                .doctorCategory(request.getDoctorCategory())
                .active(request.getActive())
                .build();

        return PageResponse.from(doctorRepository.search(criteria, pageable).map(doctorMapper::toResponse));
    }

    @Override
    public DoctorResponse update(String id, DoctorRequest request) {
        Doctor doctor = findEntityOrThrow(id);
        List<String> specialties = specialtyCatalog.normalize("specialties", request.getSpecialties());
        List<String> emergencyConditions = normalizedEmergencyConditions(request);

        if (!doctor.getTnmcNumber().equals(request.getTnmcNumber())
                && doctorRepository.existsByTnmcNumber(request.getTnmcNumber())) {
            throw new DuplicateResourceException(
                    "Doctor with TNMC number '" + request.getTnmcNumber() + "' already exists");
        }

        doctorMapper.updateEntity(doctor, request);
        doctor.setSpecialties(specialties);
        doctor.setEmergencyConditionsHandled(emergencyConditions);
        doctor.setUpdatedAt(Instant.now());

        return doctorMapper.toResponse(doctorRepository.save(doctor));
    }

    // emergencyConditionsHandled must be among the 16 SOS emergency conditions, stored as official names.
    private List<String> normalizedEmergencyConditions(DoctorRequest request) {
        return emergencyConditionCatalog.normalize("emergencyConditionsHandled", request.getEmergencyConditionsHandled());
    }

    @Override
    public void deactivate(String id) {
        Doctor doctor = findEntityOrThrow(id);
        doctor.setActive(false);
        doctor.setCurrentHospitalId(null);
        doctor.setCheckedInAt(null);
        doctor.setUpdatedAt(Instant.now());
        doctorRepository.save(doctor);
    }

    @Override
    public DoctorResponse activate(String id) {
        Doctor doctor = findEntityOrThrow(id);
        doctor.setActive(true);
        doctor.setUpdatedAt(Instant.now());
        return doctorMapper.toResponse(doctorRepository.save(doctor));
    }

    @Override
    public DoctorResponse checkIn(String id, String hospitalId) {
        Doctor doctor = findEntityOrThrow(id);

        if (!doctor.isActive()) {
            throw new IllegalArgumentException("Cannot check in an inactive doctor");
        }
        if (!hospitalRepository.existsById(hospitalId)) {
            throw new ResourceNotFoundException("Hospital not found with id '" + hospitalId + "'");
        }

        List<HospitalAssociation> associations = doctor.getHospitalAssociations();
        boolean associated = associations != null
                && associations.stream().anyMatch(assoc -> hospitalId.equals(assoc.getHospitalId()));
        if (!associated) {
            throw new IllegalArgumentException(
                    "Doctor '" + id + "' has no hospitalAssociations entry for hospital '" + hospitalId + "'");
        }

        // Checking in elsewhere implicitly checks out of wherever they were before -
        // currentHospitalId is single-valued by construction, so at most one hospital at a time.
        doctor.setCurrentHospitalId(hospitalId);
        doctor.setCheckedInAt(Instant.now());
        doctor.setUpdatedAt(Instant.now());

        return doctorMapper.toResponse(doctorRepository.save(doctor));
    }

    @Override
    public DoctorResponse checkOut(String id) {
        Doctor doctor = findEntityOrThrow(id);
        doctor.setCurrentHospitalId(null);
        doctor.setCheckedInAt(null);
        doctor.setUpdatedAt(Instant.now());
        return doctorMapper.toResponse(doctorRepository.save(doctor));
    }

    private Doctor findEntityOrThrow(String id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with id '" + id + "'"));
    }
}
