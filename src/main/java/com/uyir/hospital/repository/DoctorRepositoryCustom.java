package com.uyir.hospital.repository;

import com.uyir.hospital.model.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DoctorRepositoryCustom {

    Page<Doctor> search(DoctorSearchCriteria criteria, Pageable pageable);
}
