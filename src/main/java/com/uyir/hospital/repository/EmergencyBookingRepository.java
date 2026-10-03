package com.uyir.hospital.repository;

import com.uyir.hospital.model.EmergencyBooking;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface EmergencyBookingRepository extends MongoRepository<EmergencyBooking, String> {

    List<EmergencyBooking> findByHospitalIdOrderByRequestedAtDesc(String hospitalId);

    List<EmergencyBooking> findByPatientIdOrderByRequestedAtDesc(String patientId);

    // Half-open [from, to) so consecutive day/period windows never double-count a booking.
    @Query("{ 'hospitalId': { $in: ?0 }, 'requestedAt': { $gte: ?1, $lt: ?2 } }")
    List<EmergencyBooking> findByHospitalIdsRequestedBetween(Collection<String> hospitalIds, Instant from, Instant to);

    long countByStatus(EmergencyBookingStatus status);

    @Query(value = "{ 'status': ?0, 'requestedAt': { $gte: ?1, $lt: ?2 } }", count = true)
    long countByStatusRequestedBetween(EmergencyBookingStatus status, Instant from, Instant to);

    long countByHospitalIdInAndStatus(Collection<String> hospitalIds, EmergencyBookingStatus status);

    @Query(value = "{ 'hospitalId': { $in: ?0 }, 'status': ?1, 'requestedAt': { $gte: ?2, $lt: ?3 } }", count = true)
    long countByHospitalIdsStatusRequestedBetween(
            Collection<String> hospitalIds, EmergencyBookingStatus status, Instant from, Instant to);
}
