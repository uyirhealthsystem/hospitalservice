package com.uyir.hospital.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uyir.hospital.dto.BillingSettingsResponse;
import com.uyir.hospital.dto.CommissionInvoiceResponse;
import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.model.enums.InvoiceStatus;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.BillingService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({HospitalInvoiceController.class, BillingAdminController.class})
@AutoConfigureMockMvc(addFilters = false)
class BillingControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BillingService billingService;

    @MockitoBean
    private CurrentUserContext currentUserContext;

    private static CommissionInvoiceResponse invoice(InvoiceStatus status) {
        return CommissionInvoiceResponse.builder()
                .id("h1-2026-10").hospitalId("h1").period("2026-10")
                .appointmentCount(3).amount(new BigDecimal("60.00")).currency("INR").status(status)
                .build();
    }

    // --- hospital ---

    @Test
    void hospital_listsOwnInvoices() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");
        when(billingService.getForHospital("h1")).thenReturn(List.of(invoice(InvoiceStatus.OVERDUE)));

        mockMvc.perform(get("/api/v1/hospital/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OVERDUE"))
                .andExpect(jsonPath("$[0].amount").value(60.00));
    }

    @Test
    void hospital_pay_passesBearerToken() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");
        when(currentUserContext.getUserId()).thenReturn("u1");
        CommissionInvoiceResponse pending = invoice(InvoiceStatus.ISSUED);
        pending.setCheckoutUrl("https://rzp.io/x");
        when(billingService.startPayment("h1", "u1", "h1-2026-10", "tok")).thenReturn(pending);

        mockMvc.perform(post("/api/v1/hospital/invoices/h1-2026-10/payment").header("Authorization", "Bearer tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value("https://rzp.io/x"));
    }

    @Test
    void hospital_notHospitalRole_returns403() throws Exception {
        when(currentUserContext.requireHospitalId()).thenThrow(new ForbiddenException("nope"));

        mockMvc.perform(get("/api/v1/hospital/invoices")).andExpect(status().isForbidden());
    }

    // --- admin ---

    @Test
    void admin_updateRate() throws Exception {
        when(currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN)).thenReturn("admin1");
        when(billingService.updateSettings(any(BigDecimal.class), eq("admin1"))).thenReturn(
                BillingSettingsResponse.builder().commissionPerAppointment(new BigDecimal("25.00")).currency("INR")
                        .dueDays(15).build());

        mockMvc.perform(put("/api/v1/hospital/billing/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commissionPerAppointment\": 25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commissionPerAppointment").value(25.00));
    }

    @Test
    void admin_updateRate_negative_returns400() throws Exception {
        when(currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN)).thenReturn("admin1");

        mockMvc.perform(put("/api/v1/hospital/billing/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commissionPerAppointment\": -1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_updateRate_asHospital_returns403() throws Exception {
        when(currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN)).thenThrow(new ForbiddenException("nope"));

        mockMvc.perform(put("/api/v1/hospital/billing/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commissionPerAppointment\": 25}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_generate() throws Exception {
        when(currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN)).thenReturn("admin1");
        when(billingService.generate(YearMonth.of(2026, 10))).thenReturn(List.of(invoice(InvoiceStatus.ISSUED)));

        mockMvc.perform(post("/api/v1/hospital/billing/invoices/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\": \"2026-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("h1-2026-10"));
    }

    @Test
    void admin_generate_badMonth_returns400() throws Exception {
        when(currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN)).thenReturn("admin1");

        mockMvc.perform(post("/api/v1/hospital/billing/invoices/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\": \"2026-13\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_searchOverdue() throws Exception {
        when(currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN)).thenReturn("admin1");
        when(billingService.search(isNull(), eq(InvoiceStatus.OVERDUE))).thenReturn(List.of(invoice(InvoiceStatus.OVERDUE)));

        mockMvc.perform(get("/api/v1/hospital/billing/invoices").param("status", "OVERDUE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OVERDUE"));
    }

    @Test
    void admin_markPaid() throws Exception {
        when(currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN)).thenReturn("admin1");
        CommissionInvoiceResponse paid = invoice(InvoiceStatus.PAID);
        paid.setOfflinePaymentReference("UTR123");
        when(billingService.markPaidOffline("h1-2026-10", "UTR123", "admin1")).thenReturn(paid);

        mockMvc.perform(post("/api/v1/hospital/billing/invoices/h1-2026-10/mark-paid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\": \"UTR123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }
}
