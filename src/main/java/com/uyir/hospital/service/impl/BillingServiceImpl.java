package com.uyir.hospital.service.impl;

import com.uyir.hospital.dto.BillingSettingsResponse;
import com.uyir.hospital.dto.CommissionInvoiceResponse;
import com.uyir.hospital.dto.DoctorAppointmentBookingResponse;
import com.uyir.hospital.exception.ConflictException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.mapper.DoctorAppointmentBookingMapper;
import com.uyir.hospital.model.BillingSettings;
import com.uyir.hospital.model.CommissionInvoice;
import com.uyir.hospital.model.DoctorAppointmentBooking;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.enums.AppointmentStatus;
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
import com.uyir.hospital.service.BillingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingServiceImpl.class);

    // Billing months are IST calendar months, like token days and analytics buckets.
    static final ZoneId ZONE = AppointmentTokenGenerator.ZONE;

    private final CommissionInvoiceRepository invoiceRepository;
    private final BillingSettingsRepository settingsRepository;
    private final HospitalRepository hospitalRepository;
    private final DoctorAppointmentBookingRepository appointmentRepository;
    private final DoctorAppointmentBookingMapper appointmentMapper;
    private final MongoTemplate mongoTemplate;
    private final PaymentClient paymentClient;
    private final BillingProperties billingProperties;

    @Override
    public BigDecimal currentCommissionPerAppointment() {
        return settingsRepository.findById(BillingSettings.ID)
                .map(BillingSettings::getCommissionPerAppointment)
                .orElse(billingProperties.defaultCommissionPerAppointment());
    }

    @Override
    public BillingSettingsResponse getSettings() {
        BillingSettings stored = settingsRepository.findById(BillingSettings.ID).orElse(null);
        return BillingSettingsResponse.builder()
                .commissionPerAppointment(stored != null
                        ? stored.getCommissionPerAppointment()
                        : billingProperties.defaultCommissionPerAppointment())
                .currency(billingProperties.currency())
                .dueDays(billingProperties.dueDays())
                .updatedAt(stored != null ? stored.getUpdatedAt() : null)
                .updatedBy(stored != null ? stored.getUpdatedBy() : null)
                .build();
    }

    @Override
    public BillingSettingsResponse updateSettings(BigDecimal commissionPerAppointment, String adminUserId) {
        settingsRepository.save(BillingSettings.builder()
                .id(BillingSettings.ID)
                .commissionPerAppointment(commissionPerAppointment.setScale(2, RoundingMode.HALF_UP))
                .updatedAt(Instant.now())
                .updatedBy(adminUserId)
                .build());
        log.info("Commission per appointment set to {} by {}", commissionPerAppointment, adminUserId);
        return getSettings();
    }

    @Override
    public List<CommissionInvoiceResponse> generate(YearMonth month) {
        if (!month.isBefore(YearMonth.now(ZONE))) {
            throw new IllegalArgumentException("Can only invoice a month that has ended; " + month + " has not");
        }
        Instant from = month.atDay(1).atStartOfDay(ZONE).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant();

        // Only bookings stamped with a commission are billable, i.e. those made after commission
        // billing started; older ones were booked without the hospital agreeing to a fee.
        Query billable = Query.query(Criteria.where("appointmentDateTime").gte(from).lt(to)
                .and("status").ne(AppointmentStatus.CANCELLED)
                .and("commissionFee").ne(null));
        billable.fields().include("hospitalId").include("commissionFee");
        Map<String, List<DoctorAppointmentBooking>> byHospital = mongoTemplate
                .find(billable, DoctorAppointmentBooking.class).stream()
                .collect(Collectors.groupingBy(DoctorAppointmentBooking::getHospitalId, TreeMap::new, Collectors.toList()));

        Instant now = Instant.now();
        List<CommissionInvoiceResponse> result = new ArrayList<>();
        byHospital.forEach((hospitalId, bookings) -> {
            String id = CommissionInvoice.idFor(hospitalId, month.toString());
            CommissionInvoice existing = invoiceRepository.findById(id).orElse(null);
            if (existing != null) {
                result.add(toResponse(existing));
                return;
            }
            BigDecimal amount = bookings.stream()
                    .map(DoctorAppointmentBooking::getCommissionFee)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() == 0) {
                return; // e.g. the rate was 0 all month
            }
            CommissionInvoice invoice = CommissionInvoice.builder()
                    .id(id)
                    .hospitalId(hospitalId)
                    .hospitalName(hospitalRepository.findById(hospitalId).map(Hospital::getHospitalName).orElse(null))
                    .period(month.toString())
                    .periodStart(from)
                    .periodEnd(to)
                    .appointmentCount(bookings.size())
                    .appointmentIds(bookings.stream().map(DoctorAppointmentBooking::getId).sorted().toList())
                    .amount(amount)
                    .currency(billingProperties.currency())
                    .status(InvoiceStatus.ISSUED)
                    .issuedAt(now)
                    .dueDate(now.plus(Duration.ofDays(billingProperties.dueDays())))
                    .updatedAt(now)
                    .build();
            try {
                result.add(toResponse(invoiceRepository.insert(invoice)));
            } catch (DuplicateKeyException raced) {
                // Another pod issued it at the same moment (every replica runs the monthly job).
                invoiceRepository.findById(id).map(this::toResponse).ifPresent(result::add);
            }
        });
        log.info("Commission invoices for {}: {} hospital(s)", month, result.size());
        return result;
    }

    @Override
    public List<CommissionInvoiceResponse> search(String month, InvoiceStatus status) {
        List<CommissionInvoice> invoices;
        if (month != null) {
            invoices = invoiceRepository.findByPeriodOrderByHospitalIdAsc(month);
        } else if (status == InvoiceStatus.PAID) {
            invoices = invoiceRepository.findByStatusOrderByPeriodDesc(InvoiceStatus.PAID);
        } else if (status != null) {
            invoices = invoiceRepository.findByStatusOrderByPeriodDesc(InvoiceStatus.ISSUED);
        } else {
            invoices = invoiceRepository.findAllByOrderByPeriodDescHospitalIdAsc();
        }
        return invoices.stream()
                .map(this::toResponse)
                .filter(invoice -> status == null || invoice.getStatus() == status)
                .toList();
    }

    @Override
    public CommissionInvoiceResponse markPaidOffline(String invoiceId, String reference, String adminUserId) {
        CommissionInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> notFound(invoiceId));
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ConflictException("Invoice '" + invoiceId + "' is already paid");
        }
        Instant now = Instant.now();
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(now);
        invoice.setOfflinePaymentReference(reference);
        invoice.setMarkedPaidBy(adminUserId);
        invoice.setCheckoutUrl(null);
        invoice.setFailureReason(null);
        invoice.setUpdatedAt(now);
        log.info("Invoice {} marked paid offline by {} (ref {})", invoiceId, adminUserId, reference);
        return toResponse(invoiceRepository.save(invoice));
    }

    @Override
    public List<CommissionInvoiceResponse> getForHospital(String hospitalId) {
        return invoiceRepository.findByHospitalIdOrderByPeriodDesc(hospitalId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public CommissionInvoiceResponse getForHospital(String hospitalId, String invoiceId) {
        return toResponse(findOwned(hospitalId, invoiceId));
    }

    @Override
    public List<DoctorAppointmentBookingResponse> getAppointments(String hospitalId, String invoiceId) {
        CommissionInvoice invoice = findOwned(hospitalId, invoiceId);
        if (invoice.getAppointmentIds() == null || invoice.getAppointmentIds().isEmpty()) {
            return List.of();
        }
        return appointmentRepository.findAllById(invoice.getAppointmentIds()).stream()
                .sorted(Comparator.comparing(DoctorAppointmentBooking::getAppointmentDateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(appointmentMapper::toResponse)
                .toList();
    }

    @Override
    public CommissionInvoiceResponse startPayment(String hospitalId, String payerId, String invoiceId, String bearerToken) {
        CommissionInvoice invoice = findOwned(hospitalId, invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return toResponse(invoice);
        }
        Intent intent = paymentClient.createInvoiceIntent(invoice, payerId, bearerToken);
        invoice.setPaymentId(intent.id());
        invoice.setCheckoutUrl(intent.checkoutUrl());
        invoice.setFailureReason(null);
        invoice.setUpdatedAt(Instant.now());
        invoice = invoiceRepository.save(invoice);
        if (intent.status() == PaymentStatus.PAID) { // already settled, or the local mock provider
            invoice = confirmPaid(invoice, intent.id(), invoice.getAmount(), invoice.getCurrency());
        }
        return toResponse(invoice);
    }

    @Override
    public void onPaymentEvent(PaymentWebhookEvent event) {
        if (!PaymentClient.INVOICE_REFERENCE_TYPE.equals(event.referenceType())) {
            throw new IllegalArgumentException("referenceType must be " + PaymentClient.INVOICE_REFERENCE_TYPE);
        }
        CommissionInvoice invoice = invoiceRepository.findById(event.referenceId())
                .orElseThrow(() -> notFound(event.referenceId()));
        switch (event.status()) {
            case PAID -> confirmPaid(invoice, event.paymentId(), event.amount(), event.currency());
            case FAILED -> {
                if (invoice.getStatus() != InvoiceStatus.PAID) {
                    invoice.setFailureReason(event.failureReason());
                    invoice.setUpdatedAt(Instant.now());
                    invoiceRepository.save(invoice);
                }
            }
            case PENDING -> log.info("Invoice {} payment {} still pending", invoice.getId(), event.paymentId());
        }
    }

    @Override
    public boolean hasOverdueInvoice(String hospitalId) {
        return invoiceRepository.existsByHospitalIdAndStatusAndDueDateBefore(hospitalId, InvoiceStatus.ISSUED, Instant.now());
    }

    // Idempotent: payment-service retries the callback until it gets a 2xx.
    private CommissionInvoice confirmPaid(CommissionInvoice invoice, String paymentId, BigDecimal amount, String currency) {
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return invoice;
        }
        if (invoice.getPaymentId() != null && !invoice.getPaymentId().equals(paymentId)) {
            throw new ConflictException("Payment '" + paymentId + "' does not belong to invoice '" + invoice.getId() + "'");
        }
        if (!invoice.getCurrency().equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("currency must be " + invoice.getCurrency());
        }
        if (amount.compareTo(invoice.getAmount()) < 0) {
            log.warn("Invoice {}: paid {} but the amount is {}", invoice.getId(), amount, invoice.getAmount());
            throw new IllegalArgumentException("Paid amount is less than the invoice amount");
        }
        Instant now = Instant.now();
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaymentId(paymentId);
        invoice.setPaidAt(now);
        invoice.setCheckoutUrl(null);
        invoice.setFailureReason(null);
        invoice.setUpdatedAt(now);
        log.info("Invoice {} paid: {} {} from hospital {}", invoice.getId(), invoice.getAmount(), invoice.getCurrency(),
                invoice.getHospitalId());
        return invoiceRepository.save(invoice);
    }

    private CommissionInvoice findOwned(String hospitalId, String invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .filter(invoice -> invoice.getHospitalId().equals(hospitalId))
                .orElseThrow(() -> notFound(invoiceId));
    }

    private static ResourceNotFoundException notFound(String invoiceId) {
        return new ResourceNotFoundException("Invoice not found with id '" + invoiceId + "'");
    }

    private CommissionInvoiceResponse toResponse(CommissionInvoice invoice) {
        return CommissionInvoiceResponse.builder()
                .id(invoice.getId())
                .hospitalId(invoice.getHospitalId())
                .hospitalName(invoice.getHospitalName())
                .period(invoice.getPeriod())
                .periodStart(invoice.getPeriodStart())
                .periodEnd(invoice.getPeriodEnd())
                .appointmentCount(invoice.getAppointmentCount())
                .amount(invoice.getAmount())
                .currency(invoice.getCurrency())
                .status(invoice.isOverdue(Instant.now()) ? InvoiceStatus.OVERDUE : invoice.getStatus())
                .issuedAt(invoice.getIssuedAt())
                .dueDate(invoice.getDueDate())
                .paymentId(invoice.getPaymentId())
                .checkoutUrl(invoice.getCheckoutUrl())
                .failureReason(invoice.getFailureReason())
                .paidAt(invoice.getPaidAt())
                .offlinePaymentReference(invoice.getOfflinePaymentReference())
                .build();
    }
}
