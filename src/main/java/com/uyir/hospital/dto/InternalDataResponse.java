package com.uyir.hospital.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Only the sections that were asked for are present.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InternalDataResponse {

    private String hospitalId;
    private String doctorId;
    private Map<String, Object> hospital;
    private Map<String, Object> doctor;
    private DoctorHospitalStatus doctorStatus;
    private List<Map<String, Object>> seatedDoctors;
    private List<Map<String, Object>> onDutyDoctors;
    private SeatSummaryResponse seats;
}
