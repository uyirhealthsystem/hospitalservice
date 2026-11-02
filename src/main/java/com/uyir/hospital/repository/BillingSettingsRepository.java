package com.uyir.hospital.repository;

import com.uyir.hospital.model.BillingSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BillingSettingsRepository extends MongoRepository<BillingSettings, String> {
}
