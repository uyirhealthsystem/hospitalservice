package com.uyir.hospital.controller;

import com.uyir.hospital.dto.CommissionInvoiceResponse;
import com.uyir.hospital.dto.DoctorAppointmentBookingResponse;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.service.BillingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Hospital-only: the hospital's monthly Uyir commission invoices and paying them.
@RestController
@RequestMapping("/api/v1/hospital/invoices")
@RequiredArgsConstructor
@Tag(name = "Commission Invoices")
public class HospitalInvoiceController {

    private static final String BEARER = "Bearer ";

    private final BillingService billingService;
    private final CurrentUserContext currentUserContext;

    @GetMapping
    public List<CommissionInvoiceResponse> getMyInvoices() {
        return billingService.getForHospital(currentUserContext.requireHospitalId());
    }

    @GetMapping("/{id}")
    public CommissionInvoiceResponse getMyInvoice(@PathVariable String id) {
        return billingService.getForHospital(currentUserContext.requireHospitalId(), id);
    }

    // The appointments billed on the invoice.
    @GetMapping("/{id}/appointments")
    public List<DoctorAppointmentBookingResponse> getInvoiceAppointments(@PathVariable String id) {
        return billingService.getAppointments(currentUserContext.requireHospitalId(), id);
    }

    // Starts (or resumes) paying the invoice; open checkoutUrl to pay.
    @PostMapping("/{id}/payment")
    public CommissionInvoiceResponse pay(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String hospitalId = currentUserContext.requireHospitalId();
        return billingService.startPayment(hospitalId, currentUserContext.getUserId(), id, bearerToken(authorization));
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        return authorization.substring(BEARER.length()).trim();
    }
}
