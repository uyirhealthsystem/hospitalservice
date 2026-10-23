package com.uyir.hospital.model;

import com.uyir.hospital.model.enums.SeatType;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// One doctor slot at one hospital. Only the doctor sitting in a seat counts as that hospital's
// doctor: appointment booking and emergency-sos ignore everyone else. Seats never expire.
//
// Ids are deterministic so creating a seat twice is harmless: "<hospitalId>-free" for the default
// seat, "<purchaseId>-<n>" for paid ones (see HospitalSeatServiceImpl).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "hospital_seats")
public class HospitalSeat {

    @Id
    private String id;

    private String hospitalId;
    private SeatType seatType;

    // The SeatPurchase that paid for this seat; null for the FREE seat.
    private String purchaseId;

    // Null while the seat is empty (the field is then left out of the document). Unique per
    // hospital via a partial index in MongoIndexConfig, so a doctor holds at most one seat there.
    private String doctorId;
    private Instant assignedAt;

    private Instant createdAt;
    private Instant updatedAt;
}
