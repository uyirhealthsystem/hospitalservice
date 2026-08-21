package com.uyir.hospital.repository;

import com.uyir.hospital.model.Hospital;
import java.util.List;
import java.util.Optional;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HospitalRepository extends MongoRepository<Hospital, String>, HospitalRepositoryCustom {

    Optional<Hospital> findByRegistrationNumber(String registrationNumber);

    boolean existsByRegistrationNumber(String registrationNumber);

    List<Hospital> findByAddressLocationNear(Point point, Distance distance);
}
