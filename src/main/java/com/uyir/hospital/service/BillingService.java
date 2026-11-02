package com.uyir.hospital.service;

import com.uyir.hospital.dto.BillingSettingsResponse;
import com.uyir.hospital.dto.CommissionInvoiceResponse;
import com.uyir.hospital.dto.DoctorAppointmentBookingResponse;
import com.uyir.hospital.model.enums.InvoiceStatus;
import com.uyir.hospital.payment.PaymentWebhookEvent;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

// Uyir's per-appointment commission, billed to each hospital once a month. Emergency bookings are never billed.
public interface BillingService {

    // The rate stamped on each new appointment booking.
    BigDecimal currentCommissionPerAppointment();

    BillingSettingsResponse getSettings();

    BillingSettingsResponse updateSettings(BigDecimal commissionPerAppointment, String adminUserId);

    // Issues the month's invoices (one per hospital with billable appointments). Safe to run again:
    // invoices that already exist are returned unchanged.
    List<CommissionInvoiceResponse> generate(YearMonth month);

    // Admin: filter by month (yyyy-MM) and/or status; both optional.
    List<CommissionInvoiceResponse> search(String month, InvoiceStatus status);

    CommissionInvoiceResponse markPaidOffline(String invoiceId, String reference, String adminUserId);

    List<CommissionInvoiceResponse> getForHospital(String hospitalId);

    CommissionInvoiceResponse getForHospital(String hospitalId, String invoiceId);

    List<DoctorAppointmentBookingResponse> getAppointments(String hospitalId, String invoiceId);

    CommissionInvoiceResponse startPayment(String hospitalId, String payerId, String invoiceId, String bearerToken);

    void onPaymentEvent(PaymentWebhookEvent event);

    // True while the hospital has an unpaid invoice past its due date.
    boolean hasOverdueInvoice(String hospitalId);
}
