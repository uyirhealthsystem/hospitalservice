package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uyir.hospital.dto.CommissionInvoiceResponse;
import com.uyir.hospital.exception.ConflictException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.mapper.DoctorAppointmentBookingMapper;
import com.uyir.hospital.model.BillingSettings;
import com.uyir.hospital.model.CommissionInvoice;
import com.uyir.hospital.model.DoctorAppointmentBooking;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.enums.InvoiceStatus;
import com.uyir.hospital.payment.BillingProperties;
import com.uyir.hospital.payment.PaymentClient;
import com.uyir.hospital.payment.PaymentClient.Intent;
import com.uyir.hospital.payment.PaymentClient.PaymentStatus;
import com.uyir.hospital.payment.PaymentWebhookEvent;
import com.uyir.hospital.repository.BillingSettingsRepository;
import com.uyir.hospital.repository.CommissionInvoiceRepository;
import com.uyir.hospital.repository.DoctorAppointmentBookingRepository;
import com.uyir.hospital.repository.HospitalRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class BillingServiceImplTest {

    @Mock
    private CommissionInvoiceRepository invoiceRepository;

    @Mock
    private BillingSettingsRepository settingsRepository;

    @Mock
    private HospitalRepository hospitalRepository;

    @Mock
    private DoctorAppointmentBookingRepository appointmentRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private PaymentClient paymentClient;

    private BillingServiceImpl service;

    private final YearMonth lastMonth = YearMonth.now(BillingServiceImpl.ZONE).minusMonths(1);

    @BeforeEach
    void setUp() {
        service = new BillingServiceImpl(invoiceRepository, settingsRepository, hospitalRepository, appointmentRepository,
                new DoctorAppointmentBookingMapper(), mongoTemplate, paymentClient,
                new BillingProperties(new BigDecimal("20.00"), "INR", 15));
        lenient().when(invoiceRepository.save(any(CommissionInvoice.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(invoiceRepository.insert(any(CommissionInvoice.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static DoctorAppointmentBooking booking(String id, String hospitalId, String fee) {
        return DoctorAppointmentBooking.builder().id(id).hospitalId(hospitalId).commissionFee(new BigDecimal(fee)).build();
    }

    private CommissionInvoice issued(String hospitalId, Instant dueDate) {
        return CommissionInvoice.builder()
                .id(CommissionInvoice.idFor(hospitalId, lastMonth.toString()))
                .hospitalId(hospitalId)
                .period(lastMonth.toString())
                .amount(new BigDecimal("60.00"))
                .currency("INR")
                .appointmentCount(3)
                .status(InvoiceStatus.ISSUED)
                .dueDate(dueDate)
                .build();
    }

    // --- rate ---

    @Test
    void currentCommission_defaultsToConfiguredRate() {
        when(settingsRepository.findById(BillingSettings.ID)).thenReturn(Optional.empty());

        assertThat(service.currentCommissionPerAppointment()).isEqualByComparingTo("20.00");
    }

    @Test
    void currentCommission_adminRateWins() {
        when(settingsRepository.findById(BillingSettings.ID)).thenReturn(Optional.of(
                BillingSettings.builder().commissionPerAppointment(new BigDecimal("35.00")).build()));

        assertThat(service.currentCommissionPerAppointment()).isEqualByComparingTo("35.00");
    }

    @Test
    void updateSettings_storesRateAndAdmin() {
        when(settingsRepository.findById(BillingSettings.ID)).thenReturn(Optional.of(BillingSettings.builder()
                .commissionPerAppointment(new BigDecimal("25.00")).updatedBy("admin1").build()));

        service.updateSettings(new BigDecimal("25"), "admin1");

        ArgumentCaptor<BillingSettings> saved = ArgumentCaptor.forClass(BillingSettings.class);
        verify(settingsRepository).save(saved.capture());
        assertThat(saved.getValue().getCommissionPerAppointment()).isEqualByComparingTo("25.00");
        assertThat(saved.getValue().getUpdatedBy()).isEqualTo("admin1");
    }

    // --- generation ---

    @Test
    void generate_onePerHospital_summingStampedFees() {
        when(mongoTemplate.find(any(Query.class), eq(DoctorAppointmentBooking.class))).thenReturn(List.of(
                booking("a1", "h1", "20.00"), booking("a2", "h1", "20.00"), booking("a3", "h1", "25.00"),
                booking("a4", "h2", "20.00")));
        when(invoiceRepository.findById(any())).thenReturn(Optional.empty());
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(Hospital.builder().hospitalName("City Care").build()));

        List<CommissionInvoiceResponse> invoices = service.generate(lastMonth);

        assertThat(invoices).hasSize(2);
        CommissionInvoiceResponse h1 = invoices.get(0);
        assertThat(h1.getId()).isEqualTo("h1-" + lastMonth);
        assertThat(h1.getHospitalName()).isEqualTo("City Care");
        assertThat(h1.getAppointmentCount()).isEqualTo(3);
        assertThat(h1.getAmount()).isEqualByComparingTo("65.00");
        assertThat(h1.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(Duration.between(h1.getIssuedAt(), h1.getDueDate())).isEqualTo(Duration.ofDays(15));
        assertThat(invoices.get(1).getAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void generate_queryExcludesCancelledAndUnstampedBookings() {
        when(mongoTemplate.find(any(Query.class), eq(DoctorAppointmentBooking.class))).thenReturn(List.of());

        service.generate(lastMonth);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(DoctorAppointmentBooking.class));
        org.bson.Document q = query.getValue().getQueryObject();
        assertThat(((org.bson.Document) q.get("status")).get("$ne"))
                .isEqualTo(com.uyir.hospital.model.enums.AppointmentStatus.CANCELLED);
        assertThat((org.bson.Document) q.get("commissionFee")).containsEntry("$ne", null);
        assertThat(q).containsKey("appointmentDateTime");
    }

    @Test
    void generate_existingInvoice_isKeptAsIs() {
        CommissionInvoice existing = issued("h1", Instant.now().plus(Duration.ofDays(3)));
        when(mongoTemplate.find(any(Query.class), eq(DoctorAppointmentBooking.class)))
                .thenReturn(List.of(booking("a1", "h1", "20.00")));
        when(invoiceRepository.findById("h1-" + lastMonth)).thenReturn(Optional.of(existing));

        List<CommissionInvoiceResponse> invoices = service.generate(lastMonth);

        assertThat(invoices).singleElement().satisfies(i -> assertThat(i.getAmount()).isEqualByComparingTo("60.00"));
        verify(invoiceRepository, never()).insert(any(CommissionInvoice.class));
    }

    @Test
    void generate_racedByAnotherPod_returnsTheirInvoice() {
        CommissionInvoice theirs = issued("h1", Instant.now().plus(Duration.ofDays(15)));
        when(mongoTemplate.find(any(Query.class), eq(DoctorAppointmentBooking.class)))
                .thenReturn(List.of(booking("a1", "h1", "20.00")));
        when(invoiceRepository.findById("h1-" + lastMonth)).thenReturn(Optional.empty(), Optional.of(theirs));
        when(invoiceRepository.insert(any(CommissionInvoice.class))).thenThrow(new DuplicateKeyException("dup"));

        assertThat(service.generate(lastMonth)).singleElement()
                .satisfies(i -> assertThat(i.getAmount()).isEqualByComparingTo("60.00"));
    }

    @Test
    void generate_zeroTotal_issuesNothing() {
        when(mongoTemplate.find(any(Query.class), eq(DoctorAppointmentBooking.class)))
                .thenReturn(List.of(booking("a1", "h1", "0.00")));
        when(invoiceRepository.findById(any())).thenReturn(Optional.empty());

        assertThat(service.generate(lastMonth)).isEmpty();
        verify(invoiceRepository, never()).insert(any(CommissionInvoice.class));
    }

    @Test
    void generate_currentMonth_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.generate(YearMonth.now(BillingServiceImpl.ZONE)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- status, overdue, search ---

    @Test
    void response_issuedPastDueDate_showsOverdue() {
        when(invoiceRepository.findByHospitalIdOrderByPeriodDesc("h1"))
                .thenReturn(List.of(issued("h1", Instant.now().minus(Duration.ofDays(1)))));

        assertThat(service.getForHospital("h1")).singleElement()
                .satisfies(i -> assertThat(i.getStatus()).isEqualTo(InvoiceStatus.OVERDUE));
    }

    @Test
    void search_overdue_filtersIssuedPastDue() {
        when(invoiceRepository.findByStatusOrderByPeriodDesc(InvoiceStatus.ISSUED)).thenReturn(List.of(
                issued("h1", Instant.now().minus(Duration.ofDays(1))),
                issued("h2", Instant.now().plus(Duration.ofDays(1)))));

        assertThat(service.search(null, InvoiceStatus.OVERDUE)).extracting(CommissionInvoiceResponse::getHospitalId)
                .containsExactly("h1");
    }

    @Test
    void hasOverdueInvoice_queriesIssuedBeforeNow() {
        when(invoiceRepository.existsByHospitalIdAndStatusAndDueDateBefore(eq("h1"), eq(InvoiceStatus.ISSUED), any()))
                .thenReturn(true);

        assertThat(service.hasOverdueInvoice("h1")).isTrue();
    }

    @Test
    void getForHospital_otherHospitalsInvoice_notFound() {
        when(invoiceRepository.findById("h2-" + lastMonth)).thenReturn(Optional.of(issued("h2", Instant.now())));

        assertThatThrownBy(() -> service.getForHospital("h1", "h2-" + lastMonth))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- paying ---

    @Test
    void startPayment_returnsCheckoutUrl() {
        CommissionInvoice invoice = issued("h1", Instant.now().plus(Duration.ofDays(5)));
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(paymentClient.createInvoiceIntent(invoice, "u1", "tok"))
                .thenReturn(new Intent("pay_1", PaymentStatus.PENDING, "https://rzp.io/x"));

        CommissionInvoiceResponse response = service.startPayment("h1", "u1", invoice.getId(), "tok");

        assertThat(response.getCheckoutUrl()).isEqualTo("https://rzp.io/x");
        assertThat(response.getPaymentId()).isEqualTo("pay_1");
        assertThat(response.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
    }

    @Test
    void startPayment_alreadyPaid_doesNotCallPaymentService() {
        CommissionInvoice invoice = issued("h1", Instant.now());
        invoice.setStatus(InvoiceStatus.PAID);
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThat(service.startPayment("h1", "u1", invoice.getId(), "tok").getStatus()).isEqualTo(InvoiceStatus.PAID);
        verify(paymentClient, never()).createInvoiceIntent(any(), any(), any());
    }

    @Test
    void webhookPaid_marksInvoicePaid() {
        CommissionInvoice invoice = issued("h1", Instant.now().minus(Duration.ofDays(2)));
        invoice.setPaymentId("pay_1");
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        service.onPaymentEvent(new PaymentWebhookEvent("pay_1", PaymentClient.INVOICE_REFERENCE_TYPE, invoice.getId(),
                PaymentStatus.PAID, new BigDecimal("60.00"), "INR", null));

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getPaidAt()).isNotNull();
        assertThat(invoice.isOverdue(Instant.now())).isFalse();
    }

    @Test
    void webhookPaid_underpaid_throwsIllegalArgument() {
        CommissionInvoice invoice = issued("h1", Instant.now());
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.onPaymentEvent(new PaymentWebhookEvent("pay_1",
                        PaymentClient.INVOICE_REFERENCE_TYPE, invoice.getId(), PaymentStatus.PAID, new BigDecimal("10.00"),
                        "INR", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void webhookPaid_foreignPayment_throwsConflict() {
        CommissionInvoice invoice = issued("h1", Instant.now());
        invoice.setPaymentId("pay_1");
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.onPaymentEvent(new PaymentWebhookEvent("pay_other",
                        PaymentClient.INVOICE_REFERENCE_TYPE, invoice.getId(), PaymentStatus.PAID, new BigDecimal("60.00"),
                        "INR", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void webhookFailed_recordsReasonAndStaysIssued() {
        CommissionInvoice invoice = issued("h1", Instant.now().plus(Duration.ofDays(1)));
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        service.onPaymentEvent(new PaymentWebhookEvent("pay_1", PaymentClient.INVOICE_REFERENCE_TYPE, invoice.getId(),
                PaymentStatus.FAILED, new BigDecimal("60.00"), "INR", "card declined"));

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(invoice.getFailureReason()).isEqualTo("card declined");
    }

    @Test
    void markPaidOffline_recordsReferenceAndAdmin() {
        CommissionInvoice invoice = issued("h1", Instant.now().minus(Duration.ofDays(1)));
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        CommissionInvoiceResponse response = service.markPaidOffline(invoice.getId(), "UTR123", "admin1");

        assertThat(response.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(response.getOfflinePaymentReference()).isEqualTo("UTR123");
        assertThat(invoice.getMarkedPaidBy()).isEqualTo("admin1");
    }

    @Test
    void markPaidOffline_alreadyPaid_throwsConflict() {
        CommissionInvoice invoice = issued("h1", Instant.now());
        invoice.setStatus(InvoiceStatus.PAID);
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.markPaidOffline(invoice.getId(), "UTR123", "admin1"))
                .isInstanceOf(ConflictException.class);
    }
}
