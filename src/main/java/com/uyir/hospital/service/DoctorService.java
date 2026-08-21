package com.uyir.hospital.service;

import com.uyir.hospital.dto.DoctorRequest;
import com.uyir.hospital.dto.DoctorResponse;
import com.uyir.hospital.dto.DoctorSearchRequest;
import com.uyir.hospital.dto.PageResponse;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface DoctorService {

    DoctorResponse create(DoctorRequest request);

    DoctorResponse getById(String id);

    List<DoctorResponse> getAll();

    PageResponse<DoctorResponse> search(DoctorSearchRequest request, Pageable pageable);

    DoctorResponse update(String id, DoctorRequest request);

    void deactivate(String id);

    DoctorResponse activate(String id);

    DoctorResponse checkIn(String id, String hospitalId);

    DoctorResponse checkOut(String id);
}
