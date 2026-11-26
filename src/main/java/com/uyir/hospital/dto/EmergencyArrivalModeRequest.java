package com.uyir.hospital.dto;

import com.uyir.hospital.model.enums.ArrivalMode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Body for PATCH /emergency-bookings/{id}/arrival-mode: how the patient is getting to the hospital.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyArrivalModeRequest {
    @NotNull
    private ArrivalMode mode;
}
