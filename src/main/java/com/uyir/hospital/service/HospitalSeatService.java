package com.uyir.hospital.service;

import com.uyir.hospital.dto.HospitalSeatResponse;
import com.uyir.hospital.dto.SeatPurchaseResponse;
import com.uyir.hospital.dto.SeatSummaryResponse;
import com.uyir.hospital.payment.PaymentWebhookEvent;
import java.util.List;
import java.util.Set;

public interface HospitalSeatService {

    // Gives the hospital its one FREE seat if it doesn't have it yet. Safe to call any number of times.
    void ensureFreeSeat(String hospitalId);

    SeatSummaryResponse getSeats(String hospitalId);

    // Puts a doctor in the seat, or swaps out whoever sits there now.
    HospitalSeatResponse assignDoctor(String hospitalId, String seatId, String doctorId);

    HospitalSeatResponse removeDoctor(String hospitalId, String seatId);

    // payerId is the hospital user's AuthService id - payment-service checks it equals the bearer token's sub.
    SeatPurchaseResponse startPurchase(String hospitalId, String payerId, int quantity, String bearerToken);

    // Pay again for a PENDING/FAILED purchase, e.g. after the checkout link expired.
    SeatPurchaseResponse retryPayment(String hospitalId, String payerId, String purchaseId, String bearerToken);

    List<SeatPurchaseResponse> getPurchases(String hospitalId);

    void onPaymentEvent(PaymentWebhookEvent event);

    boolean hasSeat(String hospitalId, String doctorId);

    Set<String> seatedDoctorIds(String hospitalId);
}
