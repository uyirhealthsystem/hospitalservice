package com.uyir.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientActivityResponse {

    private String patientId;
    // Always across all of the patient's activity, whatever type filter was applied to activities.
    private PatientActivitySummary summary;
    // One page of the timeline, newest first by activityAt, so upcoming appointments lead the first page.
    private PageResponse<PatientActivityItem> activities;
}
