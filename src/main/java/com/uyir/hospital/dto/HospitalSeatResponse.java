package com.uyir.hospital.dto;

import com.uyir.hospital.model.enums.SeatType;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HospitalSeatResponse {

    private String id;
    private String hospitalId;
    private SeatType seatType;
    private String purchaseId;
    private String doctorId;
    private String doctorName;
    private Instant assignedAt;
    private Instant createdAt;
}
