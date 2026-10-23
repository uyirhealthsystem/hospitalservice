package com.uyir.hospital.controller;

import com.uyir.hospital.dto.AssignSeatDoctorRequest;
import com.uyir.hospital.dto.HospitalSeatResponse;
import com.uyir.hospital.dto.SeatPurchaseRequest;
import com.uyir.hospital.dto.SeatPurchaseResponse;
import com.uyir.hospital.dto.SeatSummaryResponse;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.service.HospitalSeatService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Hospital-only. Every hospital gets one FREE seat; each extra seat is bought for
// hospital.seat.price-per-seat. Only a doctor sitting in a seat is that hospital's doctor for
// appointment booking and emergency-sos. The HOSPITAL caller's hospital is resolved from its X-User-Id (Hospital.userId).
@RestController
@RequestMapping("/api/v1/hospital/seats")
@RequiredArgsConstructor
@Tag(name = "Doctor Seats")
public class HospitalSeatController {

    private static final String BEARER = "Bearer ";

    private final HospitalSeatService hospitalSeatService;
    private final CurrentUserContext currentUserContext;

    @GetMapping
    public SeatSummaryResponse getSeats() {
        String hospitalId = currentUserContext.requireHospitalId();
        return hospitalSeatService.getSeats(hospitalId);
    }

    // Assign a doctor to the seat, or swap out the doctor sitting there now. A swap is refused
    // while the outgoing doctor has upcoming appointments at this hospital.
    @PutMapping("/{seatId}/doctor")
    public HospitalSeatResponse assignDoctor(
            @PathVariable String seatId, @Valid @RequestBody AssignSeatDoctorRequest request) {
        String hospitalId = currentUserContext.requireHospitalId();
        return hospitalSeatService.assignDoctor(hospitalId, seatId, request.getDoctorId());
    }

    @DeleteMapping("/{seatId}/doctor")
    public HospitalSeatResponse removeDoctor(@PathVariable String seatId) {
        String hospitalId = currentUserContext.requireHospitalId();
        return hospitalSeatService.removeDoctor(hospitalId, seatId);
    }

    // Starts paying for `quantity` seats; open checkoutUrl to pay. The seats appear once
    // payment-service confirms the payment.
    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public SeatPurchaseResponse purchase(
            @Valid @RequestBody SeatPurchaseRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String hospitalId = currentUserContext.requireHospitalId();
        return hospitalSeatService.startPurchase(
                hospitalId, currentUserContext.getUserId(), request.getQuantity(), bearerToken(authorization));
    }

    // New checkout for an unpaid purchase (e.g. the 20-minute checkout link expired).
    @PostMapping("/purchases/{purchaseId}/payment")
    public SeatPurchaseResponse retryPayment(
            @PathVariable String purchaseId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String hospitalId = currentUserContext.requireHospitalId();
        return hospitalSeatService.retryPayment(
                hospitalId, currentUserContext.getUserId(), purchaseId, bearerToken(authorization));
    }

    @GetMapping("/purchases")
    public List<SeatPurchaseResponse> getPurchases() {
        String hospitalId = currentUserContext.requireHospitalId();
        return hospitalSeatService.getPurchases(hospitalId);
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        return authorization.substring(BEARER.length()).trim();
    }
}
