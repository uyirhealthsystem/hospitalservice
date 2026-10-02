package com.uyir.hospital.service;

import com.uyir.hospital.dto.AnalyticsSnapshotRequest;
import com.uyir.hospital.dto.AnalyticsSnapshotResponse;
import com.uyir.hospital.dto.AnalyticsTrendPoint;
import com.uyir.hospital.dto.DistrictAnalyticsSummary;
import com.uyir.hospital.dto.HospitalAnalyticsResponse;
import java.time.LocalDate;
import java.util.List;

public interface HospitalAnalyticsService {

    DistrictAnalyticsSummary getSummary(String district, LocalDate fromDate, LocalDate toDate);

    List<HospitalAnalyticsResponse> getHospitalBreakdown(String district, LocalDate fromDate, LocalDate toDate);

    HospitalAnalyticsResponse getHospitalAnalytics(String district, String hospitalId, LocalDate fromDate, LocalDate toDate);

    List<AnalyticsTrendPoint> getTrends(String district, LocalDate fromDate, LocalDate toDate);

    AnalyticsSnapshotResponse createSnapshot(String district, String adminId, AnalyticsSnapshotRequest request);

    List<AnalyticsSnapshotResponse> getSnapshots(String district);

    AnalyticsSnapshotResponse getSnapshot(String district, String snapshotId);

    void deleteSnapshot(String district, String snapshotId);
}
