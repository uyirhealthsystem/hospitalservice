package com.uyir.hospital.service.impl;

import com.uyir.hospital.dto.PayoutAccountRequest;
import com.uyir.hospital.dto.PayoutAccountResponse;
import com.uyir.hospital.exception.PaymentUnavailableException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.model.HospitalPayoutAccount;
import com.uyir.hospital.model.enums.PayoutAccountStatus;
import com.uyir.hospital.payment.LinkedAccountClient;
import com.uyir.hospital.payment.LinkedAccountClient.LinkedAccount;
import com.uyir.hospital.repository.HospitalPayoutAccountRepository;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.service.HospitalPayoutAccountService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HospitalPayoutAccountServiceImpl implements HospitalPayoutAccountService {

    private static final Logger log = LoggerFactory.getLogger(HospitalPayoutAccountServiceImpl.class);

    private final HospitalPayoutAccountRepository payoutAccountRepository;
    private final HospitalRepository hospitalRepository;
    private final LinkedAccountClient linkedAccountClient;

    @Override
    public PayoutAccountResponse setUp(String hospitalId, PayoutAccountRequest request, String bearerToken) {
        if (!hospitalRepository.existsById(hospitalId)) {
            throw new ResourceNotFoundException("Hospital not found with id '" + hospitalId + "'");
        }
        LinkedAccount linked = linkedAccountClient.onboard(hospitalId, request, bearerToken);
        HospitalPayoutAccount account = payoutAccountRepository.findById(hospitalId)
                .orElseGet(() -> HospitalPayoutAccount.builder().hospitalId(hospitalId).createdAt(Instant.now()).build());
        return toResponse(save(account, linked));
    }

    @Override
    public PayoutAccountResponse get(String hospitalId, String bearerToken) {
        HospitalPayoutAccount account = payoutAccountRepository.findById(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No payout account set up for hospital '" + hospitalId + "' yet"));
        if (account.getStatus() != PayoutAccountStatus.ACTIVATED && bearerToken != null) {
            try {
                account = save(account, linkedAccountClient.refresh(account.getLinkedAccountId(), bearerToken));
            } catch (PaymentUnavailableException e) {
                log.warn("Payout account of hospital {} not refreshed, returning the last known status: {}",
                        hospitalId, e.getMessage());
            }
        }
        return toResponse(account);
    }

    @Override
    public boolean payoutsEnabled(String hospitalId) {
        return payoutAccountRepository.findById(hospitalId)
                .map(account -> account.getStatus() == PayoutAccountStatus.ACTIVATED)
                .orElse(false);
    }

    private HospitalPayoutAccount save(HospitalPayoutAccount account, LinkedAccount linked) {
        if (account.getStatus() != linked.status()) {
            log.info("Payout account of hospital {}: {} -> {}", account.getHospitalId(), account.getStatus(), linked.status());
        }
        account.setLinkedAccountId(linked.id());
        account.setProviderAccountId(linked.providerAccountId());
        account.setStatus(linked.status());
        account.setRequirements(linked.requirements() == null ? List.of() : linked.requirements());
        account.setLegalBusinessName(linked.legalBusinessName());
        account.setBankAccountLast4(linked.bankAccountLast4());
        account.setIfscCode(linked.ifscCode());
        account.setBeneficiaryName(linked.beneficiaryName());
        account.setActivatedAt(linked.activatedAt());
        account.setUpdatedAt(Instant.now());
        return payoutAccountRepository.save(account);
    }

    private PayoutAccountResponse toResponse(HospitalPayoutAccount account) {
        return PayoutAccountResponse.builder()
                .hospitalId(account.getHospitalId())
                .status(account.getStatus())
                .payoutsEnabled(account.getStatus() == PayoutAccountStatus.ACTIVATED)
                .requirements(account.getRequirements())
                .legalBusinessName(account.getLegalBusinessName())
                .bankAccountLast4(account.getBankAccountLast4())
                .ifscCode(account.getIfscCode())
                .beneficiaryName(account.getBeneficiaryName())
                .activatedAt(account.getActivatedAt())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
