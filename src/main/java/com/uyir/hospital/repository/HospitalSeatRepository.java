package com.uyir.hospital.repository;

import com.uyir.hospital.model.HospitalSeat;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HospitalSeatRepository extends MongoRepository<HospitalSeat, String> {

    List<HospitalSeat> findByHospitalIdOrderByCreatedAtAsc(String hospitalId);

    List<HospitalSeat> findByHospitalIdAndDoctorIdIsNotNull(String hospitalId);

    boolean existsByHospitalIdAndDoctorId(String hospitalId, String doctorId);
}
