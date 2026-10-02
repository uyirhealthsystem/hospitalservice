package com.uyir.hospital.repository;

import com.uyir.hospital.model.AnalyticsSnapshot;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AnalyticsSnapshotRepository extends MongoRepository<AnalyticsSnapshot, String> {

    List<AnalyticsSnapshot> findByDistrictIgnoreCaseOrderByCreatedAtDesc(String district);
}
