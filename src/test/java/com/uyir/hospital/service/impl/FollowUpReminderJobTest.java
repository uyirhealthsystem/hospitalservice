package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uyir.hospital.event.FollowUpReminderDueEvent;
import com.uyir.hospital.event.HospitalEventPublisher;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.DoctorAppointmentBooking;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.embedded.ContactDetails;
import com.uyir.hospital.model.embedded.FollowUpInstruction;
import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.HospitalRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class FollowUpReminderJobTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private FollowUpStore followUpStore;

    @Mock
    private HospitalRepository hospitalRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private HospitalEventPublisher eventPublisher;

    @InjectMocks
    private FollowUpReminderJob job;

    private final LocalDate due = LocalDate.of(2026, 11, 12);

    private DoctorAppointmentBooking visit() {
        return DoctorAppointmentBooking.builder()
                .id("a1").patientId("p1").patientName("Ravi").patientEmail("ravi@test.in")
                .hospitalId("h1").doctorId("d1").status(AppointmentStatus.COMPLETED)
                .followUp(FollowUpInstruction.builder().dueDate(due.toString()).instructions("Bring reports").build())
                .build();
    }

    @Test
    void send_claimedReminder_publishesEventWithHospitalDoctorAndWindow() {
        when(mongoTemplate.find(any(Query.class), eq(DoctorAppointmentBooking.class))).thenReturn(List.of(visit()));
        when(followUpStore.claimReminder("a1", "upcomingReminderSentAt")).thenReturn(true);
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(Hospital.builder()
                .hospitalName("City Care").contactDetails(ContactDetails.builder().phone("044-1234").build()).build()));
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(Doctor.builder().name("Dr. Meena").build()));

        job.send(due.minusDays(2), due.minusDays(1), "upcomingReminderSentAt", FollowUpReminderDueEvent.UPCOMING);

        ArgumentCaptor<FollowUpReminderDueEvent> event = ArgumentCaptor.forClass(FollowUpReminderDueEvent.class);
        verify(eventPublisher).followUpReminderDue(event.capture());
        FollowUpReminderDueEvent sent = event.getValue();
        assertThat(sent.reminderType()).isEqualTo("UPCOMING");
        assertThat(sent.patientEmail()).isEqualTo("ravi@test.in");
        assertThat(sent.hospitalName()).isEqualTo("City Care");
        assertThat(sent.hospitalPhone()).isEqualTo("044-1234");
        assertThat(sent.doctorName()).isEqualTo("Dr. Meena");
        assertThat(sent.dueDate()).isEqualTo("2026-11-12");
        assertThat(sent.bookableFrom()).isEqualTo("2026-11-09");
        assertThat(sent.bookableUntil()).isEqualTo("2026-11-19");
        assertThat(sent.instructions()).isEqualTo("Bring reports");
    }

    @Test
    void send_reminderAlreadyClaimedByAnotherPod_publishesNothing() {
        when(mongoTemplate.find(any(Query.class), eq(DoctorAppointmentBooking.class))).thenReturn(List.of(visit()));
        when(followUpStore.claimReminder("a1", "dueTodayReminderSentAt")).thenReturn(false);

        job.send(due, due, "dueTodayReminderSentAt", FollowUpReminderDueEvent.DUE_TODAY);

        verify(eventPublisher, never()).followUpReminderDue(any());
    }

    @Test
    void send_queriesOnlyUnbookedUnremindedFollowUpsWithEmailInRange() {
        when(mongoTemplate.find(any(Query.class), eq(DoctorAppointmentBooking.class))).thenReturn(List.of());

        job.send(due.minusDays(2), due.minusDays(1), "upcomingReminderSentAt", FollowUpReminderDueEvent.UPCOMING);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(DoctorAppointmentBooking.class));
        Document q = query.getValue().getQueryObject();
        assertThat(q.get("status")).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat((Document) q.get("followUp.dueDate"))
                .containsEntry("$gte", "2026-11-10")
                .containsEntry("$lte", "2026-11-11");
        assertThat(q).containsEntry("followUp.followUpBookingId", null);
        assertThat(q).containsEntry("followUp.upcomingReminderSentAt", null);
        assertThat(q).containsKey("patientEmail");
    }
}
