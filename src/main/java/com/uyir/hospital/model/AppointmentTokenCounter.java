package com.uyir.hospital.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// One document per doctor per day; seq is the last token number handed out for that day.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "appointment_token_counters")
public class AppointmentTokenCounter {

    // "<doctorId>:<yyyy-MM-dd>"
    @Id
    private String id;

    private int seq;
}
