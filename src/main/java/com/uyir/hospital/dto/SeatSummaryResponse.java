package com.uyir.hospital.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatSummaryResponse {

    private String hospitalId;
    private int totalSeats;
    private int occupiedSeats;
    private int emptySeats;
    private BigDecimal pricePerSeat;
    private String currency;
    private List<HospitalSeatResponse> seats;
}
