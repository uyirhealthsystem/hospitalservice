package com.uyir.hospital.dto;

import com.uyir.hospital.model.enums.AppointmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAppointmentStatusRequest {

    // Only COMPLETED or NO_SHOW; cancel and reschedule have their own endpoints.
    @NotNull
    private AppointmentStatus status;
}
