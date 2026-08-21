package com.uyir.hospital.service.impl;

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

    @Override
    public DoctorResponse create(DoctorRequest request) {
        if (doctorRepository.existsByTnmcNumber(request.getTnmcNumber())) {
            throw new DuplicateResourceException(
                    "Doctor with TNMC number '" + request.getTnmcNumber() + "' already exists");
        }

        Doctor doctor = doctorMapper.toEntity(request);
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
        DoctorSearchCriteria criteria = DoctorSearchCriteria.builder()
                .specialty(request.getSpecialty())
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

        if (!doctor.getTnmcNumber().equals(request.getTnmcNumber())
                && doctorRepository.existsByTnmcNumber(request.getTnmcNumber())) {
            throw new DuplicateResourceException(
                    "Doctor with TNMC number '" + request.getTnmcNumber() + "' already exists");
        }

        doctorMapper.updateEntity(doctor, request);
        doctor.setUpdatedAt(Instant.now());

        return doctorMapper.toResponse(doctorRepository.save(doctor));
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
