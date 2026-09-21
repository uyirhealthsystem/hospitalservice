package com.uyir.hospital.service.impl;

// Shared input validation for geo-radius queries (findNearby, emergency-sos). MongoDB rejects
// out-of-range coordinates / a negative radius at the query level, and that raw driver exception
// isn't handled anywhere - validating here turns it into a clean 400 instead of a 500.
final class GeoQuerySupport {

    private GeoQuerySupport() {}

    static void validate(double longitude, double latitude, double radiusKm) {
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude must be between -180 and 180");
        }
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude must be between -90 and 90");
        }
        if (radiusKm <= 0) {
            throw new IllegalArgumentException("radiusKm must be greater than 0");
        }
    }
}
