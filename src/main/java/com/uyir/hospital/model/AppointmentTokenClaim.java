package com.uyir.hospital.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// One document per token in use. The _id makes a token number unique per hospital, doctor and day,
// so two concurrent bookings can never insert the same token. Deleted when the booking is cancelled
// or moved to another day, which frees the number for the next patient.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "appointment_token_claims")
public class AppointmentTokenClaim {

    // "<hospitalId>:<doctorId>:<yyyy-MM-dd>:<tokenNumber>"
    @Id
    private String id;

    private String hospitalId;
    private String doctorId;
    // Asia/Kolkata local day, yyyy-MM-dd
    private String day;
    private int tokenNumber;

    private Instant createdAt;

    public static String idFor(String hospitalId, String doctorId, String day, int tokenNumber) {
        return hospitalId + ":" + doctorId + ":" + day + ":" + tokenNumber;
    }
}
