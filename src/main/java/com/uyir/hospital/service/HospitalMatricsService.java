package com.uyir.hospital.service;

import com.uyir.hospital.dto.HospitalMatricsResponse;
import java.time.LocalDate;

public interface HospitalMatricsService {

    HospitalMatricsResponse getMatrics(LocalDate fromDate, LocalDate toDate);
}
