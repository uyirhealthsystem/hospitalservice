package com.uyir.hospital.repository;

import com.uyir.hospital.model.DoctorAppointmentBooking;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DoctorAppointmentBookingRepository extends MongoRepository<DoctorAppointmentBooking, String> {

    List<DoctorAppointmentBooking> findByHospitalIdOrderByAppointmentDateTimeAsc(String hospitalId);

    List<DoctorAppointmentBooking> findByPatientIdOrderByAppointmentDateTimeDesc(String patientId);
}
