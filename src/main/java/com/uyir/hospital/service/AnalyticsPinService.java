package com.uyir.hospital.service;

import com.uyir.hospital.dto.AnalyticsPinResetResponse;
import com.uyir.hospital.dto.AnalyticsPinStatusResponse;

// The PIN a hospital's owning account must enter every time it opens its own analytics or metrics.
// All methods take the caller's own hospital id (CurrentUserContext.requireHospitalId()).
public interface AnalyticsPinService {

    AnalyticsPinStatusResponse getStatus(String hospitalId);

    // First PIN only; 409 ANALYTICS_PIN_ALREADY_SET when one exists.
    AnalyticsPinStatusResponse setPin(String hospitalId, String pin);

    AnalyticsPinStatusResponse changePin(String hospitalId, String currentPin, String newPin);

    // Throws AnalyticsPinException unless the PIN is set, given, not locked and correct.
    void verify(String hospitalId, String pin);

    // Forgotten PIN: emails a one-time code to the hospital's contact email.
    AnalyticsPinResetResponse requestReset(String hospitalId);

    // Sets a new PIN with the emailed code; also clears any lock.
    AnalyticsPinStatusResponse confirmReset(String hospitalId, String resetCode, String newPin);
}
