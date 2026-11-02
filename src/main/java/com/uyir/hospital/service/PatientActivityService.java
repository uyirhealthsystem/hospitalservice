package com.uyir.hospital.service;

import com.uyir.hospital.dto.PatientActivityResponse;
import com.uyir.hospital.model.enums.PatientActivityType;
import org.springframework.data.domain.Pageable;

public interface PatientActivityService {

    // The patient's emergency and appointment bookings as one paged timeline; type == null means both.
    // Only the page number and size of pageable are used - the timeline is always newest first.
    PatientActivityResponse getActivity(String patientId, PatientActivityType type, Pageable pageable);
}
