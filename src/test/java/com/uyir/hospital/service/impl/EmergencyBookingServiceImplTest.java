package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uyir.hospital.ambulance.AmbulanceClient;
import com.uyir.hospital.ambulance.AmbulanceTrack;
import com.uyir.hospital.catalog.EmergencyConditionCatalog;
import com.uyir.hospital.dto.EmergencyBookingRequest;
import com.uyir.hospital.dto.EmergencyBookingResponse;
import com.uyir.hospital.dto.IncomingAmbulanceResponse;
import com.uyir.hospital.dto.IncomingPatientResponse;
import com.uyir.hospital.dto.PatientLocationRequest;
import com.uyir.hospital.model.embedded.Address;
import com.uyir.hospital.model.embedded.PatientLocation;
import com.uyir.hospital.model.enums.ArrivalMode;
import com.mongodb.client.result.UpdateResult;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import java.time.Instant;
import com.uyir.hospital.exception.ConflictException;
import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.event.BookingUpdateType;
import com.uyir.hospital.event.BookingUpdatedEvent;
import com.uyir.hospital.event.EmergencyBookingRequestedEvent;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.event.HospitalEventPublisher;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.mapper.EmergencyBookingMapper;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.EmergencyBooking;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.embedded.ContactDetails;
import com.uyir.hospital.model.embedded.EmergencyServices;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import com.uyir.hospital.repository.EmergencyBookingRepository;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.service.EmergencyCoverage;
import com.uyir.hospital.service.HospitalSeatService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bson.Document;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class EmergencyBookingServiceImplTest {

    @Mock
    private EmergencyBookingRepository emergencyBookingRepository;

    @Mock
    private HospitalRepository hospitalRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private HospitalEventPublisher hospitalEventPublisher;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private HospitalSeatService hospitalSeatService;

    @Mock
    private AmbulanceClient ambulanceClient;

    private final EmergencyBookingMapper emergencyBookingMapper = new EmergencyBookingMapper();

    // The real 16 conditions.
    private final EmergencyConditionCatalog emergencyConditionCatalog = new EmergencyConditionCatalog(JsonMapper.builder().build());

    private EmergencyBookingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmergencyBookingServiceImpl(
                emergencyBookingRepository, hospitalRepository, emergencyBookingMapper, mongoTemplate,
                hospitalEventPublisher, emergencyConditionCatalog, new EmergencyCoverage(doctorRepository, hospitalSeatService),
                ambulanceClient);
        // By default h1 is covered: one seated doctor checked in there who handles chest pain.
        lenient().when(doctorRepository.findByCurrentHospitalIdAndActiveTrue("h1")).thenReturn(List.of(Doctor.builder()
                .id("d1").active(true).currentHospitalId("h1")
                .emergencyConditionsHandled(List.of("Chest Pain / Sweating / Palpitation"))
                .build()));
        lenient().when(hospitalSeatService.seatedDoctorIds("h1")).thenReturn(Set.of("d1"));
    }

    private EmergencyBookingRequest validRequest() {
        return EmergencyBookingRequest.builder()
                .hospitalId("h1")
                .emergencyType("Chest Pain / Sweating / Palpitation")
                .patientName("John Doe")
                .patientPhone("9999999999")
                .build();
    }

    private Hospital hospitalHandling(boolean active, String... conditions) {
        return Hospital.builder()
                .id("h1")
                .active(active)
                .emergencyServices(EmergencyServices.builder()
                        .handlesEmergencies(true)
                        .specialtyEmergencyConditionsHandled(List.of(conditions))
                        .build())
                .build();
    }

    @Test
    void create_hospitalHandlesEmergencyType_savesRequestedBookingForPatient() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospitalHandling(true, "Chest Pain / Sweating / Palpitation")));
        when(emergencyBookingRepository.save(org.mockito.ArgumentMatchers.any(EmergencyBooking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmergencyBookingResponse response = service.create("p1", validRequest());

        assertThat(response.getPatientId()).isEqualTo("p1");
        assertThat(response.getHospitalId()).isEqualTo("h1");
        assertThat(response.getStatus()).isEqualTo(EmergencyBookingStatus.REQUESTED);

        ArgumentCaptor<EmergencyBooking> captor = ArgumentCaptor.forClass(EmergencyBooking.class);
        org.mockito.Mockito.verify(emergencyBookingRepository).save(captor.capture());
        assertThat(captor.getValue().getPatientId()).isEqualTo("p1");
        assertThat(captor.getValue().getRequestedAt()).isNotNull();
    }

    @Test
    void create_alertsHospitalWithBookingAndContactEmail() {
        Hospital hospital = hospitalHandling(true, "Chest Pain / Sweating / Palpitation");
        hospital.setHospitalName("City Hospital");
        hospital.setContactDetails(ContactDetails.builder().phone("0441234567").email("er@city.test").build());
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospital));
        when(emergencyBookingRepository.save(any(EmergencyBooking.class))).thenAnswer(invocation -> {
            EmergencyBooking saved = invocation.getArgument(0);
            saved.setId("b1");
            return saved;
        });

        service.create("p1", validRequest());

        ArgumentCaptor<EmergencyBookingRequestedEvent> captor = ArgumentCaptor.forClass(EmergencyBookingRequestedEvent.class);
        verify(hospitalEventPublisher).emergencyBookingRequested(captor.capture());
        verify(hospitalEventPublisher).bookingUpdated(org.mockito.ArgumentMatchers.argThat(e ->
                e.type() == BookingUpdateType.EMERGENCY_REQUESTED && "b1".equals(e.bookingId())
                        && "p1".equals(e.patientId()) && e.changedBy() == Role.PATIENT));
        EmergencyBookingRequestedEvent event = captor.getValue();
        assertThat(event.bookingId()).isEqualTo("b1");
        assertThat(event.hospitalId()).isEqualTo("h1");
        assertThat(event.hospitalName()).isEqualTo("City Hospital");
        assertThat(event.hospitalEmail()).isEqualTo("er@city.test");
        assertThat(event.patientName()).isEqualTo("John Doe");
        assertThat(event.patientPhone()).isEqualTo("9999999999");
        assertThat(event.emergencyType()).isEqualTo("Chest Pain / Sweating / Palpitation");
        assertThat(event.requestedAt()).isNotNull();
    }

    @Test
    void create_byConditionCode_storesOfficialName() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospitalHandling(true, "Chest Pain / Sweating / Palpitation")));
        when(emergencyBookingRepository.save(any(EmergencyBooking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmergencyBookingResponse response = service.create("p1", EmergencyBookingRequest.builder()
                .hospitalId("h1").emergencyType("emg001").patientName("John Doe").patientPhone("9999999999").build());

        assertThat(response.getEmergencyType()).isEqualTo("Chest Pain / Sweating / Palpitation");
    }

    @Test
    void create_emergencyTypeNotOneOfThe16_rejectedBeforeLookupAndNeverSaved() {
        assertThatThrownBy(() -> service.create("p1", EmergencyBookingRequest.builder()
                        .hospitalId("h1").emergencyType("Cardiac Arrest").patientName("John Doe").patientPhone("9999999999").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("emergencyType");

        verifyNoInteractions(hospitalRepository, emergencyBookingRepository, hospitalEventPublisher);
    }

    @Test
    void create_noDoctorOnDutyHandlesTheCondition_conflictAndNeverSaved() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospitalHandling(true, "Chest Pain / Sweating / Palpitation")));
        when(doctorRepository.findByCurrentHospitalIdAndActiveTrue("h1")).thenReturn(List.of(Doctor.builder()
                .id("d1").active(true).currentHospitalId("h1")
                .emergencyConditionsHandled(List.of("Stroke / Paralysis / Sudden Weakness"))
                .build()));

        assertThatThrownBy(() -> service.create("p1", validRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No doctor on duty")
                .hasMessageContaining("Chest Pain / Sweating / Palpitation");

        verifyNoInteractions(emergencyBookingRepository, hospitalEventPublisher);
    }

    @Test
    void create_matchingDoctorCheckedInButUnseated_conflict() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospitalHandling(true, "Chest Pain / Sweating / Palpitation")));
        when(hospitalSeatService.seatedDoctorIds("h1")).thenReturn(Set.of());

        assertThatThrownBy(() -> service.create("p1", validRequest())).isInstanceOf(ConflictException.class);
        verifyNoInteractions(emergencyBookingRepository);
    }

    @Test
    void create_hospitalEmergenciesTurnedOff_rejectedEvenWithDoctorOnDuty() {
        Hospital hospital = hospitalHandling(true, "Chest Pain / Sweating / Palpitation");
        hospital.getEmergencyServices().setHandlesEmergencies(false);
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospital));

        assertThatThrownBy(() -> service.create("p1", validRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not handle");
        verifyNoInteractions(emergencyBookingRepository);
    }

    @Test
    void create_hospitalWithoutContactDetails_stillAlertsWithNoEmail() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospitalHandling(true, "Chest Pain / Sweating / Palpitation")));
        when(emergencyBookingRepository.save(any(EmergencyBooking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create("p1", validRequest());

        ArgumentCaptor<EmergencyBookingRequestedEvent> captor = ArgumentCaptor.forClass(EmergencyBookingRequestedEvent.class);
        verify(hospitalEventPublisher).emergencyBookingRequested(captor.capture());
        assertThat(captor.getValue().hospitalEmail()).isNull();
    }

    @Test
    void create_hospitalMissing_throwsNotFound() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("p1", validRequest())).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(hospitalEventPublisher);
    }

    @Test
    void create_hospitalInactive_throwsNotFound() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospitalHandling(false, "Chest Pain / Sweating / Palpitation")));

        assertThatThrownBy(() -> service.create("p1", validRequest())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_hospitalDoesNotHandleRequestedEmergencyType_throwsIllegalArgument() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospitalHandling(true, "Burns / Electric Shock")));

        assertThatThrownBy(() -> service.create("p1", validRequest())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(hospitalEventPublisher);
    }

    @Test
    void create_hospitalIdNeverSuggestedBySos_stillRejectedIfCapabilityMismatched() {
        // Guards against a client bypassing the emergency-sos lookup and booking directly with a
        // hospitalId that doesn't actually handle the requested emergencyType.
        when(hospitalRepository.findById("h1"))
                .thenReturn(Optional.of(Hospital.builder().id("h1").active(true).build()));

        assertThatThrownBy(() -> service.create("p1", validRequest())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getByHospitalId_returnsMappedBookings() {
        EmergencyBooking booking = EmergencyBooking.builder()
                .id("b1")
                .hospitalId("h1")
                .patientId("p1")
                .status(EmergencyBookingStatus.REQUESTED)
                .build();
        when(emergencyBookingRepository.findByHospitalIdOrderByRequestedAtDesc("h1")).thenReturn(List.of(booking));

        List<EmergencyBookingResponse> result = service.getByHospitalId("h1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("b1");
    }

    @Test
    void getByPatientId_returnsMappedBookings() {
        EmergencyBooking booking = EmergencyBooking.builder()
                .id("b1")
                .hospitalId("h1")
                .patientId("p1")
                .status(EmergencyBookingStatus.REQUESTED)
                .build();
        when(emergencyBookingRepository.findByPatientIdOrderByRequestedAtDesc("p1")).thenReturn(List.of(booking));

        List<EmergencyBookingResponse> result = service.getByPatientId("p1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("b1");
    }

    private EmergencyBooking booking(EmergencyBookingStatus status) {
        return EmergencyBooking.builder().id("b1").patientId("p1").hospitalId("h1").status(status).build();
    }

    // Stubs the guarded findAndModify: returns the booking moved to `to`, as Mongo would with returnNew.
    private void stubTransitionTo(EmergencyBookingStatus to) {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                        eq(EmergencyBooking.class)))
                .thenReturn(booking(to));
    }

    private Document capturedQuery() {
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(query.capture(), any(Update.class), any(FindAndModifyOptions.class),
                eq(EmergencyBooking.class));
        return query.getValue().getQueryObject();
    }

    // The statuses the guarded update requires the booking to still be in.
    private Collection<Object> fromStatuses() {
        return new ArrayList<>((Collection<?>) ((Document) capturedQuery().get("status")).get("$in"));
    }

    private Document capturedSet() {
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class),
                eq(EmergencyBooking.class));
        return (Document) update.getValue().getUpdateObject().get("$set");
    }

    @Test
    void accept_ownRequestedBooking_movesToAcceptedOnlyIfStillRequested() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.REQUESTED)));
        stubTransitionTo(EmergencyBookingStatus.ACCEPTED);

        EmergencyBookingResponse response = service.accept("b1", "h1");

        assertThat(response.getStatus()).isEqualTo(EmergencyBookingStatus.ACCEPTED);
        assertThat(capturedQuery().get("_id")).isEqualTo("b1");
        assertThat(fromStatuses()).containsExactly(EmergencyBookingStatus.REQUESTED);
        Document set = capturedSet();
        assertThat(set.get("status")).isEqualTo(EmergencyBookingStatus.ACCEPTED);
        assertThat(set).containsKeys("acceptedAt", "updatedAt");
        ArgumentCaptor<BookingUpdatedEvent> live = ArgumentCaptor.forClass(BookingUpdatedEvent.class);
        verify(hospitalEventPublisher).bookingUpdated(live.capture());
        assertThat(live.getValue().type()).isEqualTo(BookingUpdateType.EMERGENCY_ACCEPTED);
        assertThat(live.getValue().bookingType()).isEqualTo(BookingUpdatedEvent.BookingKind.EMERGENCY);
        assertThat(live.getValue().status()).isEqualTo("ACCEPTED");
        assertThat(live.getValue().changedBy()).isEqualTo(Role.HOSPITAL);
    }

    @Test
    void accept_otherHospitalsBooking_throwsForbiddenWithoutUpdating() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.REQUESTED)));

        assertThatThrownBy(() -> service.accept("b1", "h2")).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void accept_missingBooking_throwsNotFound() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept("b1", "h1")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void accept_noLongerRequested_throwsConflictNamingCurrentStatus() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.CANCELLED)));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                        eq(EmergencyBooking.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> service.accept("b1", "h1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CANCELLED");
        verifyNoInteractions(hospitalEventPublisher);
    }

    @Test
    void reject_storesReason() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.REQUESTED)));
        stubTransitionTo(EmergencyBookingStatus.REJECTED);

        service.reject("b1", "h1", "No ICU bed free");

        Document set = capturedSet();
        assertThat(set.get("status")).isEqualTo(EmergencyBookingStatus.REJECTED);
        assertThat(set.get("rejectionReason")).isEqualTo("No ICU bed free");
        assertThat(set).containsKey("rejectedAt");
    }

    @Test
    void complete_onlyFromAccepted() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.ACCEPTED)));
        stubTransitionTo(EmergencyBookingStatus.COMPLETED);

        service.complete("b1", "h1");

        assertThat(fromStatuses()).containsExactly(EmergencyBookingStatus.ACCEPTED);
        assertThat(capturedSet()).containsKey("completedAt");
    }

    @Test
    void cancel_ownBooking_allowedFromRequestedOrAccepted() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.ACCEPTED)));
        stubTransitionTo(EmergencyBookingStatus.CANCELLED);

        EmergencyBookingResponse response = service.cancel("b1", "p1", "Went to a closer hospital");

        assertThat(response.getStatus()).isEqualTo(EmergencyBookingStatus.CANCELLED);
        assertThat(fromStatuses())
                .containsExactlyInAnyOrder(EmergencyBookingStatus.REQUESTED, EmergencyBookingStatus.ACCEPTED);
        assertThat(capturedSet().get("cancellationReason")).isEqualTo("Went to a closer hospital");
    }

    @Test
    void cancel_otherPatientsBooking_throwsForbiddenWithoutUpdating() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.REQUESTED)));

        assertThatThrownBy(() -> service.cancel("b1", "p2", "x")).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(mongoTemplate);
    }

    // ---------------------------------------------------------------- ambulance link + hospital tracking

    private static AmbulanceTrack ride(String patientId, String status, boolean withLocation) {
        return new AmbulanceTrack(patientId,
                new AmbulanceTrack.Booking("amb1", "EMERGENCY", status, "CARDIAC", "ALS",
                        new AmbulanceTrack.Place(13.05, 80.21, "Home"), new AmbulanceTrack.Place(13.08, 80.27, "City Hospital"),
                        null, null, Instant.parse("2026-11-25T10:00:00Z")),
                new AmbulanceTrack.Driver("Ravi", "9000000000", "TN01AB1234", "ALS", "ON_TRIP"),
                withLocation ? new AmbulanceTrack.Location(13.06, 80.24, 90.0, Instant.parse("2026-11-25T10:05:00Z")) : null,
                withLocation ? new AmbulanceTrack.Eta(7, 3.2) : null);
    }

    private EmergencyBooking linked(EmergencyBookingStatus status) {
        EmergencyBooking b = booking(status);
        b.setAmbulanceBookingId("amb1");
        b.setPatientName("John Doe");
        return b;
    }

    @Test
    void linkAmbulance_patientsOwnRide_storedWithoutChangingStatus() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.ACCEPTED)));
        when(ambulanceClient.track("amb1")).thenReturn(Optional.of(ride("p1", "SEARCHING", false)));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(EmergencyBooking.class))).thenReturn(linked(EmergencyBookingStatus.ACCEPTED));

        EmergencyBookingResponse response = service.linkAmbulance("b1", "p1", "amb1");

        assertThat(response.getAmbulanceBookingId()).isEqualTo("amb1");
        assertThat(fromStatuses()).containsExactlyInAnyOrder(EmergencyBookingStatus.REQUESTED, EmergencyBookingStatus.ACCEPTED);
        Document set = capturedSet();
        assertThat(set.get("ambulanceBookingId")).isEqualTo("amb1");
        assertThat(set).containsKeys("ambulanceLinkedAt", "updatedAt").doesNotContainKey("status");
        verify(hospitalEventPublisher).bookingUpdated(org.mockito.ArgumentMatchers.argThat(e ->
                e.type() == BookingUpdateType.EMERGENCY_AMBULANCE_LINKED && e.changedBy() == Role.PATIENT));
    }

    @Test
    void linkAmbulance_someoneElsesRide_forbiddenWithoutUpdating() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.ACCEPTED)));
        when(ambulanceClient.track("amb1")).thenReturn(Optional.of(ride("p2", "SEARCHING", false)));

        assertThatThrownBy(() -> service.linkAmbulance("b1", "p1", "amb1")).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void linkAmbulance_unknownRide_notFound() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.ACCEPTED)));
        when(ambulanceClient.track("amb1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.linkAmbulance("b1", "p1", "amb1")).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void linkAmbulance_otherPatientsEmergency_forbiddenBeforeCallingAmbulanceService() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.ACCEPTED)));

        assertThatThrownBy(() -> service.linkAmbulance("b1", "p2", "amb1")).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(ambulanceClient, mongoTemplate);
    }

    @Test
    void incomingAmbulance_onTheWay_showsPositionEtaAndDriver() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(linked(EmergencyBookingStatus.ACCEPTED)));
        when(ambulanceClient.track("amb1")).thenReturn(Optional.of(ride("p1", "PICKED_UP", true)));

        IncomingAmbulanceResponse r = service.incomingAmbulance("b1", "h1");

        assertThat(r.getStatus()).isEqualTo("PICKED_UP");
        assertThat(r.isPatientOnBoard()).isTrue();
        assertThat(r.getLocation().getLat()).isEqualTo(13.06);
        assertThat(r.getEtaMinutes()).isEqualTo(7L);
        assertThat(r.getDriver().getPlateNumber()).isEqualTo("TN01AB1234");
        assertThat(r.getPickup().getAddress()).isEqualTo("Home");
        assertThat(r.getPatientName()).isEqualTo("John Doe");
    }

    @Test
    void incomingAmbulance_rideOver_hidesDriversPosition() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(linked(EmergencyBookingStatus.ACCEPTED)));
        when(ambulanceClient.track("amb1")).thenReturn(Optional.of(ride("p1", "COMPLETED", true)));

        IncomingAmbulanceResponse r = service.incomingAmbulance("b1", "h1");

        assertThat(r.getLocation()).isNull();
        assertThat(r.getEtaMinutes()).isNull();
    }

    @Test
    void incomingAmbulance_otherHospital_forbidden() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(linked(EmergencyBookingStatus.ACCEPTED)));

        assertThatThrownBy(() -> service.incomingAmbulance("b1", "h2")).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(ambulanceClient);
    }

    @Test
    void incomingAmbulance_notYetAccepted_conflict() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(linked(EmergencyBookingStatus.REQUESTED)));

        assertThatThrownBy(() -> service.incomingAmbulance("b1", "h1")).isInstanceOf(ConflictException.class);
        verifyNoInteractions(ambulanceClient);
    }

    @Test
    void incomingAmbulance_noAmbulanceLinked_notFound() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.ACCEPTED)));

        assertThatThrownBy(() -> service.incomingAmbulance("b1", "h1")).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(ambulanceClient);
    }

    @Test
    void linkAmbulance_alsoRecordsUyirAmbulanceAsArrivalMode() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.ACCEPTED)));
        when(ambulanceClient.track("amb1")).thenReturn(Optional.of(ride("p1", "SEARCHING", false)));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(EmergencyBooking.class))).thenReturn(linked(EmergencyBookingStatus.ACCEPTED));

        service.linkAmbulance("b1", "p1", "amb1");

        assertThat(capturedSet().get("arrivalMode")).isEqualTo(ArrivalMode.UYIR_AMBULANCE);
    }

    @Test
    void setArrivalMode_ownOpenEmergency_storedWithoutChangingStatus() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.ACCEPTED)));
        EmergencyBooking updated = booking(EmergencyBookingStatus.ACCEPTED);
        updated.setArrivalMode(ArrivalMode.OTHER_AMBULANCE);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(EmergencyBooking.class))).thenReturn(updated);

        EmergencyBookingResponse r = service.setArrivalMode("b1", "p1", ArrivalMode.OTHER_AMBULANCE);

        assertThat(r.getArrivalMode()).isEqualTo(ArrivalMode.OTHER_AMBULANCE);
        assertThat(fromStatuses()).containsExactlyInAnyOrder(EmergencyBookingStatus.REQUESTED, EmergencyBookingStatus.ACCEPTED);
        assertThat(capturedSet()).containsKey("arrivalMode").doesNotContainKey("status");
        verify(hospitalEventPublisher).bookingUpdated(org.mockito.ArgumentMatchers.argThat(e ->
                e.type() == BookingUpdateType.EMERGENCY_ARRIVAL_MODE_SET && e.changedBy() == Role.PATIENT));
    }

    @Test
    void setArrivalMode_otherPatientsEmergency_forbidden() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.ACCEPTED)));

        assertThatThrownBy(() -> service.setArrivalMode("b1", "p2", ArrivalMode.OWN_WAY)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(mongoTemplate);
    }

    private PatientLocationRequest fix(Instant recordedAt) {
        return PatientLocationRequest.builder()
                .lat(13.05).lng(80.25).accuracy(12.0).heading(90.0).speed(8.0).recordedAt(recordedAt).build();
    }

    private void stubLocationUpdate(long matched) {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(EmergencyBooking.class)))
                .thenReturn(UpdateResult.acknowledged(matched, matched, null));
    }

    private PatientLocation storedFix() {
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), update.capture(), eq(EmergencyBooking.class));
        return (PatientLocation) ((Document) update.getValue().getUpdateObject().get("$set")).get("patientLocation");
    }

    @Test
    void updatePatientLocation_ownOpenEmergency_storesFixWithoutLiveEvent() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.ACCEPTED)));
        stubLocationUpdate(1);
        Instant taken = Instant.now().minusSeconds(5);

        service.updatePatientLocation("b1", "p1", fix(taken));

        PatientLocation stored = storedFix();
        assertThat(stored.getLat()).isEqualTo(13.05);
        assertThat(stored.getRecordedAt()).isEqualTo(taken);
        assertThat(stored.getUpdatedAt()).isNotNull();
        verifyNoInteractions(hospitalEventPublisher);
    }

    @Test
    void updatePatientLocation_phoneClockAhead_countsAsTakenNow() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.ACCEPTED)));
        stubLocationUpdate(1);

        service.updatePatientLocation("b1", "p1", fix(Instant.now().plusSeconds(3600)));

        assertThat(storedFix().getRecordedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void updatePatientLocation_olderThanStoredFix_ignoredQuietly() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.ACCEPTED)));
        stubLocationUpdate(0);

        service.updatePatientLocation("b1", "p1", fix(Instant.now().minusSeconds(600)));
    }

    @Test
    void updatePatientLocation_emergencyOver_conflict() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.COMPLETED)));
        stubLocationUpdate(0);

        assertThatThrownBy(() -> service.updatePatientLocation("b1", "p1", fix(null))).isInstanceOf(ConflictException.class);
    }

    @Test
    void updatePatientLocation_otherPatientsEmergency_forbidden() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(booking(EmergencyBookingStatus.ACCEPTED)));

        assertThatThrownBy(() -> service.updatePatientLocation("b1", "p2", fix(null))).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(mongoTemplate);
    }

    private EmergencyBooking sharing(EmergencyBookingStatus status) {
        EmergencyBooking b = booking(status);
        b.setPatientName("John Doe");
        b.setArrivalMode(ArrivalMode.OTHER_AMBULANCE);
        b.setPatientLocation(PatientLocation.builder()
                .lat(13.0827).lng(80.2707).recordedAt(Instant.now()).updatedAt(Instant.now()).build());
        return b;
    }

    private Hospital hospitalAt(double lat, double lng) {
        Address address = new Address();
        address.setLocation(new GeoJsonPoint(lng, lat));
        return Hospital.builder().id("h1").address(address).build();
    }

    @Test
    void incomingPatient_onTheWay_showsPositionAndDistanceToHospital() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(sharing(EmergencyBookingStatus.ACCEPTED)));
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospitalAt(13.0604, 80.2496)));

        IncomingPatientResponse r = service.incomingPatient("b1", "h1");

        assertThat(r.getArrivalMode()).isEqualTo(ArrivalMode.OTHER_AMBULANCE);
        assertThat(r.getLocation().getLat()).isEqualTo(13.0827);
        assertThat(r.getHospital().getLat()).isEqualTo(13.0604);
        assertThat(r.getDistanceKm()).isBetween(3.0, 3.5);
        assertThat(r.getPatientName()).isEqualTo("John Doe");
    }

    @Test
    void incomingPatient_emergencyCompleted_hidesPosition() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(sharing(EmergencyBookingStatus.COMPLETED)));
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospitalAt(13.0604, 80.2496)));

        IncomingPatientResponse r = service.incomingPatient("b1", "h1");

        assertThat(r.getLocation()).isNull();
        assertThat(r.getDistanceKm()).isNull();
    }

    @Test
    void incomingPatient_otherHospital_forbidden() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(sharing(EmergencyBookingStatus.ACCEPTED)));

        assertThatThrownBy(() -> service.incomingPatient("b1", "h2")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void incomingPatient_notYetAccepted_conflict() {
        when(emergencyBookingRepository.findById("b1")).thenReturn(Optional.of(sharing(EmergencyBookingStatus.REQUESTED)));

        assertThatThrownBy(() -> service.incomingPatient("b1", "h1")).isInstanceOf(ConflictException.class);
    }
}
