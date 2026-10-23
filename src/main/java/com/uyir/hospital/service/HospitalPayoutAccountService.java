package com.uyir.hospital.service;

import com.uyir.hospital.dto.PayoutAccountRequest;
import com.uyir.hospital.dto.PayoutAccountResponse;

public interface HospitalPayoutAccountService {

    // Registers (or continues registering / changes) the hospital's bank account with Razorpay
    // Route through payment-service.
    PayoutAccountResponse setUp(String hospitalId, PayoutAccountRequest request, String bearerToken);

    // The hospital's payout account; while it is not ACTIVATED yet, its status is refreshed from
    // payment-service first (the cached status is returned if that is unreachable).
    PayoutAccountResponse get(String hospitalId, String bearerToken);

    // True when the hospital can be paid out to (Razorpay activated its account).
    boolean payoutsEnabled(String hospitalId);
}
