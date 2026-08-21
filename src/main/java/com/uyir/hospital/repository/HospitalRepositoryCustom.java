package com.uyir.hospital.repository;

import com.uyir.hospital.model.Hospital;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HospitalRepositoryCustom {

    Page<Hospital> search(HospitalSearchCriteria criteria, Pageable pageable);
}
