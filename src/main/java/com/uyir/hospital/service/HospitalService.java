package com.uyir.hospital.service;

import com.uyir.hospital.dto.HospitalRequest;
import com.uyir.hospital.dto.HospitalResponse;
import com.uyir.hospital.dto.PageResponse;
import com.uyir.hospital.model.enums.HospitalType;
import com.uyir.hospital.model.enums.OwnershipType;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface HospitalService {

    HospitalResponse create(HospitalRequest request);

    HospitalResponse getById(String id);

    PageResponse<HospitalResponse> search(
            String city,
            String state,
            HospitalType hospitalType,
            OwnershipType ownershipType,
            Boolean active,
            Pageable pageable);

    HospitalResponse update(String id, HospitalRequest request);

    void deactivate(String id);

    HospitalResponse activate(String id);

    List<HospitalResponse> findNearby(double longitude, double latitude, double radiusKm);
}
