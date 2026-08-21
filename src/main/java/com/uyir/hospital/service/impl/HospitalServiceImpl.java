package com.uyir.hospital.service.impl;

import com.uyir.hospital.dto.HospitalRequest;
import com.uyir.hospital.dto.HospitalResponse;
import com.uyir.hospital.dto.PageResponse;
import com.uyir.hospital.exception.DuplicateResourceException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.mapper.HospitalMapper;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.enums.HospitalType;
import com.uyir.hospital.model.enums.OwnershipType;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.repository.HospitalSearchCriteria;
import com.uyir.hospital.service.HospitalService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HospitalServiceImpl implements HospitalService {

    private final HospitalRepository hospitalRepository;
    private final HospitalMapper hospitalMapper;

    @Override
    public HospitalResponse create(HospitalRequest request) {
        if (hospitalRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new DuplicateResourceException(
                    "Hospital with registration number '" + request.getRegistrationNumber() + "' already exists");
        }

        Hospital hospital = hospitalMapper.toEntity(request);
        hospital.setActive(true);
        hospital.setCreatedAt(Instant.now());
        hospital.setUpdatedAt(Instant.now());

        return hospitalMapper.toResponse(hospitalRepository.save(hospital));
    }

    @Override
    public HospitalResponse getById(String id) {
        return hospitalMapper.toResponse(findEntityOrThrow(id));
    }

    @Override
    public PageResponse<HospitalResponse> search(
            String city,
            String state,
            HospitalType hospitalType,
            OwnershipType ownershipType,
            Boolean active,
            Pageable pageable) {

        HospitalSearchCriteria criteria = HospitalSearchCriteria.builder()
                .city(city)
                .state(state)
                .hospitalType(hospitalType)
                .ownershipType(ownershipType)
                .active(active)
                .build();

        return PageResponse.from(hospitalRepository.search(criteria, pageable).map(hospitalMapper::toResponse));
    }

    @Override
    public HospitalResponse update(String id, HospitalRequest request) {
        Hospital hospital = findEntityOrThrow(id);

        if (!hospital.getRegistrationNumber().equals(request.getRegistrationNumber())
                && hospitalRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new DuplicateResourceException(
                    "Hospital with registration number '" + request.getRegistrationNumber() + "' already exists");
        }

        hospitalMapper.updateEntity(hospital, request);
        hospital.setUpdatedAt(Instant.now());

        return hospitalMapper.toResponse(hospitalRepository.save(hospital));
    }

    @Override
    public void deactivate(String id) {
        Hospital hospital = findEntityOrThrow(id);
        hospital.setActive(false);
        hospital.setUpdatedAt(Instant.now());
        hospitalRepository.save(hospital);
    }

    @Override
    public HospitalResponse activate(String id) {
        Hospital hospital = findEntityOrThrow(id);
        hospital.setActive(true);
        hospital.setUpdatedAt(Instant.now());
        return hospitalMapper.toResponse(hospitalRepository.save(hospital));
    }

    @Override
    public List<HospitalResponse> findNearby(double longitude, double latitude, double radiusKm) {
        Point point = new Point(longitude, latitude);
        Distance distance = new Distance(radiusKm, Metrics.KILOMETERS);

        return hospitalRepository.findByAddressLocationNear(point, distance).stream()
                .map(hospitalMapper::toResponse)
                .toList();
    }

    private Hospital findEntityOrThrow(String id) {
        return hospitalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found with id '" + id + "'"));
    }
}
