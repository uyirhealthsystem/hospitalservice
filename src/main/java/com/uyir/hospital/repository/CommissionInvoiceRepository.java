package com.uyir.hospital.repository;

import com.uyir.hospital.model.CommissionInvoice;
import com.uyir.hospital.model.enums.InvoiceStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CommissionInvoiceRepository extends MongoRepository<CommissionInvoice, String> {

    List<CommissionInvoice> findByHospitalIdOrderByPeriodDesc(String hospitalId);

    List<CommissionInvoice> findByPeriodOrderByHospitalIdAsc(String period);

    List<CommissionInvoice> findByStatusOrderByPeriodDesc(InvoiceStatus status);

    List<CommissionInvoice> findAllByOrderByPeriodDescHospitalIdAsc();

    // An unpaid invoice past its due date: the hospital can't take new appointments until it pays.
    boolean existsByHospitalIdAndStatusAndDueDateBefore(String hospitalId, InvoiceStatus status, Instant now);
}
