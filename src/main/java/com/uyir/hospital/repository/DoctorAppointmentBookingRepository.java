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

    // Candidates for a slot clash: the caller narrows by each booking's own end time.
    @Query("{ 'doctorId': ?0, 'status': { $in: ?1 }, 'appointmentDateTime': { $gte: ?2, $lt: ?3 } }")
    List<DoctorAppointmentBooking> findByDoctorIdStatusInStartingBetween(
            String doctorId, Collection<AppointmentStatus> statuses, Instant from, Instant to);

    // Half-open [from, to) so consecutive day/period windows never double-count an appointment.
    @Query("{ 'hospitalId': { $in: ?0 }, 'appointmentDateTime': { $gte: ?1, $lt: ?2 } }")
    List<DoctorAppointmentBooking> findByHospitalIdsScheduledBetween(Collection<String> hospitalIds, Instant from, Instant to);

    @Query(value = "{ 'status': ?0, 'appointmentDateTime': { $gte: ?1, $lt: ?2 } }", count = true)
    long countByStatusScheduledBetween(AppointmentStatus status, Instant from, Instant to);

    @Query(value = "{ 'status': { $in: ?0 }, 'appointmentDateTime': { $gte: ?1 } }", count = true)
    long countByStatusInScheduledFrom(Collection<AppointmentStatus> statuses, Instant from);

    @Query(value = "{ 'hospitalId': { $in: ?0 }, 'status': ?1, 'appointmentDateTime': { $gte: ?2, $lt: ?3 } }", count = true)
    long countByHospitalIdsStatusScheduledBetween(
            Collection<String> hospitalIds, AppointmentStatus status, Instant from, Instant to);

    @Query(value = "{ 'hospitalId': { $in: ?0 }, 'status': { $in: ?1 }, 'appointmentDateTime': { $gte: ?2 } }", count = true)
    long countByHospitalIdsStatusInScheduledFrom(
            Collection<String> hospitalIds, Collection<AppointmentStatus> statuses, Instant from);
}
