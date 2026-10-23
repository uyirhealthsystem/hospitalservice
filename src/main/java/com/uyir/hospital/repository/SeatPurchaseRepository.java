package com.uyir.hospital.repository;

import com.uyir.hospital.model.SeatPurchase;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SeatPurchaseRepository extends MongoRepository<SeatPurchase, String> {

    List<SeatPurchase> findByHospitalIdOrderByCreatedAtDesc(String hospitalId);
}
