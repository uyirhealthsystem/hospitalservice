package com.uyir.hospital.service.impl;

import com.uyir.hospital.service.BillingService;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Issues last month's commission invoices early on the 1st (IST). Every replica runs this; invoice ids
// are one per hospital per month, so only the first insert wins and the rest are no-ops.
// An admin can also run it by hand: POST /api/v1/hospital/billing/invoices/generate.
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hospital.billing.schedule-enabled", havingValue = "true", matchIfMissing = true)
public class CommissionInvoiceScheduler {

    private static final Logger log = LoggerFactory.getLogger(CommissionInvoiceScheduler.class);

    private final BillingService billingService;

    @Scheduled(cron = "${hospital.billing.generate-cron:0 30 0 1 * *}", zone = "Asia/Kolkata")
    public void issueLastMonth() {
        YearMonth lastMonth = YearMonth.now(BillingServiceImpl.ZONE).minusMonths(1);
        try {
            billingService.generate(lastMonth);
        } catch (RuntimeException e) {
            log.error("Monthly commission invoicing for {} failed; run it again via the admin endpoint", lastMonth, e);
        }
    }
}
