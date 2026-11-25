package com.uyir.hospital.service.impl;

import com.uyir.hospital.ambulance.AmbulanceClient;
import com.uyir.hospital.ambulance.AmbulanceTrack;
import com.uyir.hospital.catalog.EmergencyConditionCatalog;
import com.uyir.hospital.dto.EmergencyBookingRequest;
import com.uyir.hospital.dto.EmergencyBookingResponse;
import com.uyir.hospital.dto.IncomingAmbulanceResponse;
import com.uyir.hospital.event.EmergencyBookingRequestedEvent;
import com.uyir.hospital.event.BookingUpdateType;
import com.uyir.hospital.event.BookingUpdatedEvent;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.event.HospitalEventPublisher;
import com.uyir.hospital.exception.ConflictException;
import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.mapper.EmergencyBookingMapper;
import com.uyir.hospital.model.EmergencyBooking;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import com.uyir.hospital.repository.EmergencyBookingRepository;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.service.EmergencyBookingService;
import com.uyir.hospital.service.EmergencyCoverage;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmergencyBookingServiceImpl implements EmergencyBookingService {

    // A patient can link an ambulance until the hospital closes or either side ends the emergency.
    private static final Set<EmergencyBookingStatus> LINKABLE =
            EnumSet.of(EmergencyBookingStatus.REQUESTED, EmergencyBookingStatus.ACCEPTED);
    // A hospital follows the ambulance once it has accepted (COMPLETED: still shows how the ride ended).
    private static final Set<EmergencyBookingStatus> TRACKABLE =
            EnumSet.of(EmergencyBookingStatus.ACCEPTED, EmergencyBookingStatus.COMPLETED);
    // Ambulance ride statuses while it is on the road (the ambulance service's BookingStatus).
    private static final Set<String> RIDE_LIVE = Set.of("SEARCHING", "EN_ROUTE_TO_PICKUP", "ARRIVED", "PICKED_UP");

    private final EmergencyBookingRepository emergencyBookingRepository;
    private final HospitalRepository hospitalRepository;
    private final EmergencyBookingMapper emergencyBookingMapper;
    private final MongoTemplate mongoTemplate;
    private final HospitalEventPublisher hospitalEventPublisher;
    private final EmergencyConditionCatalog emergencyConditionCatalog;
    private final EmergencyCoverage emergencyCoverage;
    private final AmbulanceClient ambulanceClient;

    @Override
    public EmergencyBookingResponse create(String patientId, EmergencyBookingRequest request) {
        // One of the 16 SOS emergency conditions (code or name) -> its official name; 400 otherwise.
        String emergencyType = emergencyConditionCatalog.requireName(request.getEmergencyType());
        Hospital hospital = hospitalRepository
                .findById(request.getHospitalId())
                .filter(Hospital::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found with id '" + request.getHospitalId() + "'"));

        // Re-validate the same rules emergency-sos used to suggest this hospital: the client picks a hospitalId
        // from those suggestions, but nothing stops a direct call with an id that was never suggested, or one
        // whose only matching doctor checked out since.
        if (!emergencyCoverage.hospitalHandles(hospital, emergencyType)) {
            throw new IllegalArgumentException(
                    "Hospital '" + request.getHospitalId() + "' does not handle emergency type '" + emergencyType + "'");
        }
        if (emergencyCoverage.onDutyDoctorsHandling(hospital, emergencyType).isEmpty()) {
            throw new ConflictException("No doctor on duty at hospital '" + request.getHospitalId()
                    + "' handles '" + emergencyType + "' right now - choose another hospital");
        }

        Instant now = Instant.now();
        EmergencyBooking booking = EmergencyBooking.builder()
                .patientId(patientId)
                .patientName(request.getPatientName())
                .patientPhone(request.getPatientPhone())
                .hospitalId(request.getHospitalId())
                .emergencyType(emergencyType)
                .status(EmergencyBookingStatus.REQUESTED)
                .requestedAt(now)
                .updatedAt(now)
                .build();

        EmergencyBooking saved = emergencyBookingRepository.save(booking);
        // Alert the hospital (live dashboard + email). Best-effort: never fails or delays the booking.
        hospitalEventPublisher.emergencyBookingRequested(EmergencyBookingRequestedEvent.of(saved, hospital));
        // Live WebSocket update for the hospital and the patient's other devices.
        hospitalEventPublisher.bookingUpdated(
                BookingUpdatedEvent.of(BookingUpdateType.EMERGENCY_REQUESTED, saved, Role.PATIENT));
        return emergencyBookingMapper.toResponse(saved);
    }

    @Override
    public EmergencyBookingResponse accept(String id, String hospitalId) {
        findOwnedByHospital(id, hospitalId);
        Instant now = Instant.now();
        return transition(id, EnumSet.of(EmergencyBookingStatus.REQUESTED), EmergencyBookingStatus.ACCEPTED,
                new Update().set("acceptedAt", now), now, BookingUpdateType.EMERGENCY_ACCEPTED, Role.HOSPITAL);
    }

    @Override
    public EmergencyBookingResponse reject(String id, String hospitalId, String reason) {
        findOwnedByHospital(id, hospitalId);
        Instant now = Instant.now();
        return transition(id, EnumSet.of(EmergencyBookingStatus.REQUESTED), EmergencyBookingStatus.REJECTED,
                new Update().set("rejectedAt", now).set("rejectionReason", reason), now, BookingUpdateType.EMERGENCY_REJECTED, Role.HOSPITAL);
    }

    @Override
    public EmergencyBookingResponse complete(String id, String hospitalId) {
        findOwnedByHospital(id, hospitalId);
        Instant now = Instant.now();
        return transition(id, EnumSet.of(EmergencyBookingStatus.ACCEPTED), EmergencyBookingStatus.COMPLETED,
                new Update().set("completedAt", now), now, BookingUpdateType.EMERGENCY_COMPLETED, Role.HOSPITAL);
    }

    @Override
    public EmergencyBookingResponse cancel(String id, String patientId, String reason) {
        EmergencyBooking booking = findById(id);
        if (!booking.getPatientId().equals(patientId)) {
            throw new ForbiddenException("Emergency booking '" + id + "' does not belong to patient '" + patientId + "'");
        }
        Instant now = Instant.now();
        return transition(id, EnumSet.of(EmergencyBookingStatus.REQUESTED, EmergencyBookingStatus.ACCEPTED),
                EmergencyBookingStatus.CANCELLED,
                new Update().set("cancelledAt", now).set("cancellationReason", reason), now, BookingUpdateType.EMERGENCY_CANCELLED, Role.PATIENT);
    }

    @Override
    public EmergencyBookingResponse linkAmbulance(String id, String patientId, String ambulanceBookingId) {
        EmergencyBooking booking = findById(id);
        if (!booking.getPatientId().equals(patientId)) {
            throw new ForbiddenException("Emergency booking '" + id + "' does not belong to patient '" + patientId + "'");
        }
        // The ride must exist and be this patient's own: otherwise a patient could show the hospital someone else's ride.
        AmbulanceTrack ride = ambulanceClient.track(ambulanceBookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Ambulance booking not found with id '" + ambulanceBookingId + "'"));
        if (!patientId.equals(ride.patientId())) {
            throw new ForbiddenException("Ambulance booking '" + ambulanceBookingId + "' does not belong to patient '" + patientId + "'");
        }
        Instant now = Instant.now();
        return transition(id, LINKABLE, null,
                new Update().set("ambulanceBookingId", ambulanceBookingId).set("ambulanceLinkedAt", now), now,
                BookingUpdateType.EMERGENCY_AMBULANCE_LINKED, Role.PATIENT);
    }

    @Override
    public IncomingAmbulanceResponse incomingAmbulance(String id, String hospitalId) {
        EmergencyBooking booking = findById(id);
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new ForbiddenException("Emergency booking '" + id + "' does not belong to hospital '" + hospitalId + "'");
        }
        if (!TRACKABLE.contains(booking.getStatus())) {
            throw new ConflictException("Emergency booking '" + id + "' is " + booking.getStatus()
                    + ": the ambulance can be followed once it is ACCEPTED");
        }
        if (booking.getAmbulanceBookingId() == null) {
            throw new ResourceNotFoundException("No ambulance is bringing the patient of emergency booking '" + id + "'");
        }
        AmbulanceTrack ride = ambulanceClient.track(booking.getAmbulanceBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Ambulance booking '" + booking.getAmbulanceBookingId() + "' no longer exists"));
        if (!booking.getPatientId().equals(ride.patientId())) {
            throw new ForbiddenException("The linked ambulance does not belong to this emergency's patient");
        }
        return toIncoming(booking, ride);
    }

    private static IncomingAmbulanceResponse toIncoming(EmergencyBooking booking, AmbulanceTrack ride) {
        AmbulanceTrack.Booking b = ride.booking();
        // Only while the ride is under way: the driver's position is not the hospital's to see after the drop.
        boolean live = RIDE_LIVE.contains(b.status());
        AmbulanceTrack.Driver d = ride.driver();
        AmbulanceTrack.Location l = live ? ride.location() : null;
        AmbulanceTrack.Eta eta = live ? ride.eta() : null;
        return IncomingAmbulanceResponse.builder()
                .emergencyBookingId(booking.getId())
                .ambulanceBookingId(b.id())
                .patientName(booking.getPatientName())
                .patientPhone(booking.getPatientPhone())
                .emergencyType(booking.getEmergencyType())
                .status(b.status())
                .patientOnBoard("PICKED_UP".equals(b.status()))
                .ambulanceType(b.ambulanceType())
                .pickup(place(b.pickup()))
                .destination(place(b.drop()))
                .driver(d == null ? null : IncomingAmbulanceResponse.Driver.builder()
                        .name(d.name()).phone(d.phone()).plateNumber(d.plateNumber()).ambulanceType(d.ambulanceType())
                        .build())
                .location(l == null ? null : IncomingAmbulanceResponse.Location.builder()
                        .lat(l.lat()).lng(l.lng()).heading(l.heading()).updatedAt(l.at()).build())
                .etaMinutes(eta == null ? null : eta.minutes())
                .distanceKm(eta == null ? null : eta.distanceKm())
                .bookedAt(b.createdAt())
                .build();
    }

    private static IncomingAmbulanceResponse.Place place(AmbulanceTrack.Place p) {
        return p == null ? null : IncomingAmbulanceResponse.Place.builder().lat(p.lat()).lng(p.lng()).address(p.address()).build();
    }

    // Applies the change only if the booking is still in one of the `from` statuses, in a single
    // findAndModify - so a patient cancelling while the hospital accepts can't overwrite each other.
    private EmergencyBookingResponse transition(
            String id, Set<EmergencyBookingStatus> from, EmergencyBookingStatus to, Update update, Instant now,
            BookingUpdateType updateType, Role changedBy) {
        // to == null: a change that keeps the status (e.g. linking an ambulance), still only in a `from` status.
        if (to != null) {
            update.set("status", to);
        }
        EmergencyBooking updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(id).and("status").in(from)),
                update.set("updatedAt", now),
                FindAndModifyOptions.options().returnNew(true),
                EmergencyBooking.class);
        if (updated == null) {
            EmergencyBookingStatus current = findById(id).getStatus();
            throw new ConflictException(to == null
                    ? "Cannot change emergency booking '" + id + "' while it is " + current
                    : "Cannot change emergency booking '" + id + "' from " + current + " to " + to);
        }
        hospitalEventPublisher.bookingUpdated(BookingUpdatedEvent.of(updateType, updated, changedBy));
        return emergencyBookingMapper.toResponse(updated);
    }

    private void findOwnedByHospital(String id, String hospitalId) {
        if (!findById(id).getHospitalId().equals(hospitalId)) {
            throw new ForbiddenException("Emergency booking '" + id + "' does not belong to hospital '" + hospitalId + "'");
        }
    }

    private EmergencyBooking findById(String id) {
        return emergencyBookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Emergency booking not found with id '" + id + "'"));
    }

    @Override
    public List<EmergencyBookingResponse> getByHospitalId(String hospitalId) {
        return emergencyBookingRepository.findByHospitalIdOrderByRequestedAtDesc(hospitalId).stream()
                .map(emergencyBookingMapper::toResponse)
                .toList();
    }

    @Override
    public List<EmergencyBookingResponse> getByPatientId(String patientId) {
        return emergencyBookingRepository.findByPatientIdOrderByRequestedAtDesc(patientId).stream()
                .map(emergencyBookingMapper::toResponse)
                .toList();
    }
}
