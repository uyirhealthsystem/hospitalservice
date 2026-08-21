package com.uyir.hospital.repository;

import com.uyir.hospital.model.Doctor;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DoctorRepository extends MongoRepository<Doctor, String>, DoctorRepositoryCustom {

    boolean existsByTnmcNumber(String tnmcNumber);

    List<Doctor> findByCurrentHospitalIdAndActiveTrue(String currentHospitalId);
}
