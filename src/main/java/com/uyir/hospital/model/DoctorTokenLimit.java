package com.uyir.hospital.model;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// How many appointment tokens a hospital issues for one of its doctors per day.
// dailyLimit applies every day; dateLimits overrides it for a specific local date (0 = doctor not
// taking appointments that day). No document, or a null dailyLimit, means no limit.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "doctor_token_limits")
public class DoctorTokenLimit {

    // "<hospitalId>:<doctorId>"
    @Id
    private String id;

    private String hospitalId;
    private String doctorId;

    private Integer dailyLimit;

    // ISO date (yyyy-MM-dd, Asia/Kolkata day) -> limit for that day.
    @Builder.Default
    private Map<String, Integer> dateLimits = new TreeMap<>();

    private Instant updatedAt;

    public static String idFor(String hospitalId, String doctorId) {
        return hospitalId + ":" + doctorId;
    }
}
