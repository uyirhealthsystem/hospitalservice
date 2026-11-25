package com.uyir.hospital.ambulance;

import java.util.Optional;

public interface AmbulanceClient {

    // The ride's live tracking; empty when the ambulance service has no such booking.
    // Throws AmbulanceUnavailableException when the ambulance service can't be reached or isn't configured.
    Optional<AmbulanceTrack> track(String ambulanceBookingId);
}
