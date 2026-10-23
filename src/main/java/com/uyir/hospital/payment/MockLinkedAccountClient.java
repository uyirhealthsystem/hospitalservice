package com.uyir.hospital.payment;

import com.uyir.hospital.dto.PayoutAccountRequest;
import com.uyir.hospital.model.enums.PayoutAccountStatus;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// LOCAL TESTING ONLY (hospital.payout.provider=mock): every payout account is ACTIVATED at once.
@Component
@ConditionalOnProperty(name = "hospital.payout.provider", havingValue = "mock")
public class MockLinkedAccountClient implements LinkedAccountClient {

    private static final Logger log = LoggerFactory.getLogger(MockLinkedAccountClient.class);

    public MockLinkedAccountClient() {
        log.warn("Using the MOCK payout account provider: no bank account is verified. Never enable this in production.");
    }

    @Override
    public LinkedAccount onboard(String hospitalId, PayoutAccountRequest request, String bearerToken) {
        PayoutAccountRequest.BankAccount bank = request.getBankAccount();
        String number = bank.getAccountNumber();
        return new LinkedAccount("mock-" + hospitalId, PayoutAccountStatus.ACTIVATED, "acc_mock_" + hospitalId, List.of(),
                request.getLegalBusinessName(), number.substring(number.length() - 4), bank.getIfscCode(),
                bank.getBeneficiaryName(), Instant.now());
    }

    @Override
    public LinkedAccount refresh(String linkedAccountId, String bearerToken) {
        throw new IllegalStateException("mock payout accounts are ACTIVATED at once and never refreshed");
    }
}
