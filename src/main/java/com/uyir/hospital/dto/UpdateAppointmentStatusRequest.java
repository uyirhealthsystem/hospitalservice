package com.uyir.hospital.dto;

import com.uyir.hospital.model.enums.AppointmentStatus;
import jakarta.validation.Valid;
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

    // Optional, only with COMPLETED: ask the patient to come back. Can also be set later
    // with PUT /doctor-appointments/{id}/follow-up.
    @Valid
    private FollowUpRequest followUp;
}
