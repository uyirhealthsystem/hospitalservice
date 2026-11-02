package com.uyir.hospital.config;

import com.uyir.hospital.model.AnalyticsSnapshot;
import com.uyir.hospital.model.AppointmentTokenClaim;
import com.uyir.hospital.model.CommissionInvoice;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.DoctorAppointmentBooking;
import com.uyir.hospital.model.EmergencyBooking;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.HospitalSeat;
import com.uyir.hospital.model.SeatPurchase;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeospatialIndex;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
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
        hospitalIndexOps.ensureIndex(new Index().on("address.district", Direction.ASC));
        hospitalIndexOps.ensureIndex(new Index().on("hospitalType", Direction.ASC));
        hospitalIndexOps.ensureIndex(new Index().on("active", Direction.ASC));
        // One hospital per AuthService user. Partial so hospitals created before userId existed don't clash.
        hospitalIndexOps.ensureIndex(new Index()
                .on("userId", Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("userId").exists(true))));

        IndexOperations doctorIndexOps = mongoTemplate.indexOps(Doctor.class);
        doctorIndexOps.ensureIndex(new Index().on("specialties", Direction.ASC));
        doctorIndexOps.ensureIndex(new Index().on("hospitalAssociations.hospitalId", Direction.ASC));
        doctorIndexOps.ensureIndex(new Index().on("active", Direction.ASC));
        doctorIndexOps.ensureIndex(new Index().on("currentHospitalId", Direction.ASC));

        IndexOperations emergencyBookingIndexOps = mongoTemplate.indexOps(EmergencyBooking.class);
        emergencyBookingIndexOps.ensureIndex(new Index().on("hospitalId", Direction.ASC));
        emergencyBookingIndexOps.ensureIndex(new Index().on("patientId", Direction.ASC));

        IndexOperations appointmentBookingIndexOps = mongoTemplate.indexOps(DoctorAppointmentBooking.class);
        appointmentBookingIndexOps.ensureIndex(new Index().on("hospitalId", Direction.ASC));
        appointmentBookingIndexOps.ensureIndex(new Index().on("patientId", Direction.ASC));
        appointmentBookingIndexOps.ensureIndex(
                new Index().on("doctorId", Direction.ASC).on("appointmentDateTime", Direction.ASC));

        IndexOperations seatIndexOps = mongoTemplate.indexOps(HospitalSeat.class);
        seatIndexOps.ensureIndex(new Index().on("hospitalId", Direction.ASC).on("createdAt", Direction.ASC));
        // A doctor sits in at most one seat per hospital. Partial so the many empty seats (no doctorId) don't clash.
        seatIndexOps.ensureIndex(new Index()
                .on("hospitalId", Direction.ASC)
                .on("doctorId", Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("doctorId").exists(true))));

        IndexOperations seatPurchaseIndexOps = mongoTemplate.indexOps(SeatPurchase.class);
        seatPurchaseIndexOps.ensureIndex(new Index().on("hospitalId", Direction.ASC).on("createdAt", Direction.DESC));

        IndexOperations analyticsSnapshotIndexOps = mongoTemplate.indexOps(AnalyticsSnapshot.class);
        analyticsSnapshotIndexOps.ensureIndex(new Index().on("district", Direction.ASC).on("createdAt", Direction.DESC));

        // Monthly commission invoicing scans one month of appointments across all hospitals.
        appointmentBookingIndexOps.ensureIndex(new Index().on("appointmentDateTime", Direction.ASC));

        IndexOperations tokenClaimIndexOps = mongoTemplate.indexOps(AppointmentTokenClaim.class);
        tokenClaimIndexOps.ensureIndex(
                new Index().on("hospitalId", Direction.ASC).on("doctorId", Direction.ASC).on("day", Direction.ASC));

        IndexOperations invoiceIndexOps = mongoTemplate.indexOps(CommissionInvoice.class);
        invoiceIndexOps.ensureIndex(new Index().on("hospitalId", Direction.ASC).on("period", Direction.DESC));
        invoiceIndexOps.ensureIndex(new Index().on("period", Direction.ASC));
        invoiceIndexOps.ensureIndex(new Index().on("status", Direction.ASC).on("dueDate", Direction.ASC));
    }
}
