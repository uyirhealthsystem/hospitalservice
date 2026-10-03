package com.uyir.hospital.repository;

import com.uyir.hospital.model.DoctorAppointmentBooking;
import com.uyir.hospital.model.enums.AppointmentStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface DoctorAppointmentBookingRepository extends MongoRepository<DoctorAppointmentBooking, String> {

    List<DoctorAppointmentBooking> findByHospitalIdOrderByAppointmentDateTimeAsc(String hospitalId);

    List<DoctorAppointmentBooking> findByPatientIdOrderByAppointmentDateTimeDesc(String patientId);

    // Half-open [from, to) so consecutive day/period windows never double-count an appointment.
    @Query("{ 'hospitalId': { $in: ?0 }, 'appointmentDateTime': { $gte: ?1, $lt: ?2 } }")
    List<DoctorAppointmentBooking> findByHospitalIdsScheduledBetween(Collection<String> hospitalIds, Instant from, Instant to);

    @Query(value = "{ 'status': ?0, 'appointmentDateTime': { $gte: ?1, $lt: ?2 } }", count = true)
    long countByStatusScheduledBetween(AppointmentStatus status, Instant from, Instant to);

    @Query(value = "{ 'status': { $in: ?0 }, 'appointmentDateTime': { $gte: ?1 } }", count = true)
    long countByStatusInScheduledFrom(Collection<AppointmentStatus> statuses, Instant from);
}
