package com.uyir.hospital.repository;

import com.uyir.hospital.model.EmergencyBooking;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EmergencyBookingRepository extends MongoRepository<EmergencyBooking, String> {

    List<EmergencyBooking> findByHospitalIdOrderByRequestedAtDesc(String hospitalId);

    List<EmergencyBooking> findByPatientIdOrderByRequestedAtDesc(String patientId);
}
