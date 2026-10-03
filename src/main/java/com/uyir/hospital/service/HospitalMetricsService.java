package com.uyir.hospital.service;

import com.uyir.hospital.dto.HospitalMetricsResponse;
import java.time.LocalDate;

public interface HospitalMetricsService {

    HospitalMetricsResponse getMetrics(LocalDate fromDate, LocalDate toDate);

    HospitalMetricsResponse getDistrictMetrics(String district, LocalDate fromDate, LocalDate toDate);
}
