package com.uyir.hospital.controller;

import com.uyir.hospital.dto.PayoutAccountRequest;
import com.uyir.hospital.dto.PayoutAccountResponse;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.service.HospitalPayoutAccountService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Hospital-only. The bank account patients' appointment payments are paid out to: registered with
// Razorpay Route through payment-service, then verified by Razorpay (status ACTIVATED). The
// HOSPITAL caller's hospital is resolved from its X-User-Id (Hospital.userId).
@RestController
@RequestMapping("/api/v1/hospital/payout-account")
@RequiredArgsConstructor
@Tag(name = "Payout Account")
public class HospitalPayoutAccountController {

    private static final String BEARER = "Bearer ";

    private final HospitalPayoutAccountService payoutAccountService;
    private final CurrentUserContext currentUserContext;

    // Set up, continue (after NEEDS_CLARIFICATION or a failed attempt) or change the bank account.
    @PutMapping
    public PayoutAccountResponse setUp(
            @Valid @RequestBody PayoutAccountRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String hospitalId = currentUserContext.requireHospitalId();
        return payoutAccountService.setUp(hospitalId, request, bearerToken(authorization));
    }

    @GetMapping
    public PayoutAccountResponse get(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String hospitalId = currentUserContext.requireHospitalId();
        return payoutAccountService.get(hospitalId, bearerToken(authorization));
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        return authorization.substring(BEARER.length()).trim();
    }
}
