package com.uyir.hospital.dto;

import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpResponse {

    private LocalDate dueDate;
    private String instructions;
    // The FOLLOW_UP appointment can be booked for a date in [bookableFrom, bookableUntil].
    private LocalDate bookableFrom;
    private LocalDate bookableUntil;
    // Set once the patient has booked the follow-up.
    private String followUpBookingId;
    private Instant setAt;
}
