package com.uyir.hospital.repository;

import com.uyir.hospital.model.Hospital;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface HospitalRepository extends MongoRepository<Hospital, String>, HospitalRepositoryCustom {

    Optional<Hospital> findByRegistrationNumber(String registrationNumber);

    boolean existsByRegistrationNumber(String registrationNumber);

    boolean existsByUserId(String userId);

    Optional<Hospital> findByUserId(String userId);

    List<Hospital> findByAddressLocationNear(Point point, Distance distance);

    List<Hospital> findByAddressDistrictIgnoreCase(String district);

    long countByActiveTrue();

    // Active hospitals in emergency mode right now: flag on and lease not over (no lease = from before leases
    // existed, still on until EmergencyServicesExpiryJob gives it one).
    @Query(value = "{ 'active': true, 'emergencyServices.handlesEmergencies': true, '$or': [ "
            + "{ 'emergencyServices.emergencyOnUntil': { '$gt': ?0 } }, { 'emergencyServices.emergencyOnUntil': null } ] }",
            count = true)
    long countHandlingEmergenciesAt(Instant now);
}
