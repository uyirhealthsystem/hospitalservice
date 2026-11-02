package com.uyir.hospital.controller;

import com.uyir.hospital.dto.BillingSettingsRequest;
import com.uyir.hospital.dto.BillingSettingsResponse;
import com.uyir.hospital.dto.CommissionInvoiceResponse;
import com.uyir.hospital.dto.GenerateInvoicesRequest;
import com.uyir.hospital.dto.MarkInvoicePaidRequest;
import com.uyir.hospital.model.enums.InvoiceStatus;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.BillingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// ADMIN / SUPER_ADMIN: the commission rate and every hospital's invoices.
@RestController
@RequestMapping("/api/v1/hospital/billing")
@RequiredArgsConstructor
@Tag(name = "Commission Billing (admin)")
public class BillingAdminController {

    private final BillingService billingService;
    private final CurrentUserContext currentUserContext;

    @GetMapping("/settings")
    public BillingSettingsResponse getSettings() {
        requireAdmin();
        return billingService.getSettings();
    }

    // Applies to appointments booked from now on; existing bookings keep the rate they were booked at.
    @PutMapping("/settings")
    public BillingSettingsResponse updateSettings(@Valid @RequestBody BillingSettingsRequest request) {
        String adminId = requireAdmin();
        return billingService.updateSettings(request.getCommissionPerAppointment(), adminId);
    }

    // Normally runs by itself on the 1st; use this to (re)run a past month. Existing invoices are kept as they are.
    @PostMapping("/invoices/generate")
    public List<CommissionInvoiceResponse> generate(@Valid @RequestBody GenerateInvoicesRequest request) {
        requireAdmin();
        return billingService.generate(parseMonth(request.getMonth()));
    }

    @GetMapping("/invoices")
    public List<CommissionInvoiceResponse> search(
            @RequestParam(required = false) String month, @RequestParam(required = false) InvoiceStatus status) {
        requireAdmin();
        if (month != null) {
            parseMonth(month); // 400 on a bad month instead of an empty list
        }
        return billingService.search(month, status);
    }

    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("month must be yyyy-MM");
        }
    }

    // For a payment made outside the app (bank transfer, cheque). Unblocks the hospital's bookings.
    @PostMapping("/invoices/{id}/mark-paid")
    public CommissionInvoiceResponse markPaid(@PathVariable String id, @Valid @RequestBody MarkInvoicePaidRequest request) {
        String adminId = requireAdmin();
        return billingService.markPaidOffline(id, request.getReference(), adminId);
    }

    private String requireAdmin() {
        return currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN);
    }
}
