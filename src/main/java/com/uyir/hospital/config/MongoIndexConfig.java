package com.uyir.hospital.config;

import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.Hospital;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeospatialIndex;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void ensureIndexes() {
        IndexOperations hospitalIndexOps = mongoTemplate.indexOps(Hospital.class);
        hospitalIndexOps.ensureIndex(new GeospatialIndex("address.location").typed(GeoSpatialIndexType.GEO_2DSPHERE));
        hospitalIndexOps.ensureIndex(new Index().on("address.city", Direction.ASC));
        hospitalIndexOps.ensureIndex(new Index().on("hospitalType", Direction.ASC));
        hospitalIndexOps.ensureIndex(new Index().on("active", Direction.ASC));

        IndexOperations doctorIndexOps = mongoTemplate.indexOps(Doctor.class);
        doctorIndexOps.ensureIndex(new Index().on("specialties", Direction.ASC));
        doctorIndexOps.ensureIndex(new Index().on("hospitalAssociations.hospitalId", Direction.ASC));
        doctorIndexOps.ensureIndex(new Index().on("active", Direction.ASC));
        doctorIndexOps.ensureIndex(new Index().on("currentHospitalId", Direction.ASC));
    }
}
