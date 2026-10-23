package com.uyir.hospital.service.impl;

import com.uyir.hospital.catalog.EmergencyConditionCatalog;
import com.uyir.hospital.catalog.SpecialtyCatalog;
import com.uyir.hospital.dto.HospitalRequest;
import com.uyir.hospital.dto.HospitalResponse;
import com.uyir.hospital.dto.PageResponse;
import com.uyir.hospital.exception.DuplicateResourceException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.mapper.HospitalMapper;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.embedded.EmergencyServices;
import com.uyir.hospital.model.embedded.Facilities;
import com.uyir.hospital.model.enums.HospitalType;
import com.uyir.hospital.model.enums.OwnershipType;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.repository.HospitalSearchCriteria;
import com.uyir.hospital.service.HospitalSeatService;
import com.uyir.hospital.service.HospitalService;
import java.time.Instant;
import java.util.Comparator;
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
    private final HospitalSeatService hospitalSeatService;
    private final SpecialtyCatalog specialtyCatalog;
    private final EmergencyConditionCatalog emergencyConditionCatalog;

    @Override
    public HospitalResponse create(String userId, HospitalRequest request) {
        List<String> departments = normalizedDepartments(request);
        List<String> conditions = normalizedConditions(request);
        if (hospitalRepository.existsByUserId(userId)) {
            throw new DuplicateResourceException("User '" + userId + "' has already created a hospital");
        }
        if (hospitalRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new DuplicateResourceException(
                    "Hospital with registration number '" + request.getRegistrationNumber() + "' already exists");
        }

        Hospital hospital = hospitalMapper.toEntity(request);
        applyCatalogValues(hospital, departments, conditions);
        hospital.setUserId(userId);
        hospital.setActive(true);
        hospital.setCreatedAt(Instant.now());
        hospital.setUpdatedAt(Instant.now());

        Hospital saved = hospitalRepository.save(hospital);
        hospitalSeatService.ensureFreeSeat(saved.getId());
        return hospitalMapper.toResponse(saved);
    }

    @Override
    public HospitalResponse getById(String id) {
        return hospitalMapper.toResponse(findEntityOrThrow(id));
    }

    @Override
    public HospitalResponse getByUserId(String userId) {
        return hospitalRepository.findByUserId(userId)
                .map(hospitalMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No hospital found for user '" + userId + "'"));
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
        List<String> departments = normalizedDepartments(request);
        List<String> conditions = normalizedConditions(request);

        if (!hospital.getRegistrationNumber().equals(request.getRegistrationNumber())
                && hospitalRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new DuplicateResourceException(
                    "Hospital with registration number '" + request.getRegistrationNumber() + "' already exists");
        }

        hospitalMapper.updateEntity(hospital, request);
        applyCatalogValues(hospital, departments, conditions);
        hospital.setUpdatedAt(Instant.now());

        return hospitalMapper.toResponse(hospitalRepository.save(hospital));
    }

    // facilities.specialtyDepartments (appointment booking) must come from the specialty master list, same rule
    // as a doctor's specialties, stored as official names. Validated before anything is looked up or saved.
    private List<String> normalizedDepartments(HospitalRequest request) {
        Facilities facilities = request.getFacilities();
        return facilities == null ? null
                : specialtyCatalog.normalize("facilities.specialtyDepartments", facilities.getSpecialtyDepartments());
    }

    // emergencyServices.specialtyEmergencyConditionsHandled (emergency booking) must be among the 16 SOS emergency
    // conditions - independent of the departments. Stored as official names, which is what SOS and emergency
    // booking match the patient's emergencyType against.
    private List<String> normalizedConditions(HospitalRequest request) {
        EmergencyServices emergencyServices = request.getEmergencyServices();
        return emergencyServices == null ? null
                : emergencyConditionCatalog.normalize(
                        "emergencyServices.specialtyEmergencyConditionsHandled", emergencyServices.getSpecialtyEmergencyConditionsHandled());
    }

    private static void applyCatalogValues(Hospital hospital, List<String> departments, List<String> conditions) {
        if (hospital.getFacilities() != null) {
            hospital.getFacilities().setSpecialtyDepartments(departments);
        }
        if (hospital.getEmergencyServices() != null) {
            hospital.getEmergencyServices().setSpecialtyEmergencyConditionsHandled(conditions);
        }
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
    public HospitalResponse setHandlesEmergencies(String id, boolean handlesEmergencies) {
        Hospital hospital = findEntityOrThrow(id);

        EmergencyServices emergencyServices = hospital.getEmergencyServices();
        if (emergencyServices == null) {
            emergencyServices = EmergencyServices.builder().build();
            hospital.setEmergencyServices(emergencyServices);
        }
        emergencyServices.setHandlesEmergencies(handlesEmergencies);
        hospital.setUpdatedAt(Instant.now());

        return hospitalMapper.toResponse(hospitalRepository.save(hospital));
    }

    @Override
    public List<HospitalResponse> findNearby(double longitude, double latitude, double radiusKm) {
        GeoQuerySupport.validate(longitude, latitude, radiusKm);

        Point point = new Point(longitude, latitude);
        Distance distance = new Distance(radiusKm, Metrics.KILOMETERS);

        return hospitalRepository.findByAddressLocationNear(point, distance).stream()
                .map(hospitalMapper::toResponse)
                .toList();
    }

    @Override
    public List<HospitalResponse> findByDistrict(String district, Boolean active) {
        if (district == null || district.isBlank()) {
            throw new IllegalArgumentException("district must not be blank");
        }
        return hospitalRepository.findByAddressDistrictIgnoreCase(district.trim()).stream()
                .filter(hospital -> active == null || hospital.isActive() == active)
                .sorted(Comparator.comparing(Hospital::getHospitalName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(hospitalMapper::toResponse)
                .toList();
    }

    private Hospital findEntityOrThrow(String id) {
        return hospitalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found with id '" + id + "'"));
    }
}
