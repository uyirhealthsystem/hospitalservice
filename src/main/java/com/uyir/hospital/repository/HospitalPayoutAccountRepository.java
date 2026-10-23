package com.uyir.hospital.repository;

import com.uyir.hospital.model.HospitalPayoutAccount;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HospitalPayoutAccountRepository extends MongoRepository<HospitalPayoutAccount, String> {
}
