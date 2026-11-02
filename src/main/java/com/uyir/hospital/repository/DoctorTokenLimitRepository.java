package com.uyir.hospital.repository;

import com.uyir.hospital.model.DoctorTokenLimit;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DoctorTokenLimitRepository extends MongoRepository<DoctorTokenLimit, String> {

    List<DoctorTokenLimit> findByHospitalIdOrderByDoctorIdAsc(String hospitalId);
}
