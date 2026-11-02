package com.uyir.hospital.service.impl;

import com.uyir.hospital.event.FollowUpReminderDueEvent;
import com.uyir.hospital.event.HospitalEventPublisher;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.DoctorAppointmentBooking;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.embedded.FollowUpInstruction;
import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.HospitalRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Emails patients about follow-ups they haven't booked yet: once when it is 1-2 days away (UPCOMING)
// and once on the due date (DUE_TODAY). Runs hourly in the daytime so a restart doesn't skip a day;
// FollowUpStore.claimReminder makes sure each reminder goes out once across all replicas.
// Only appointments with a patientEmail get reminders.
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hospital.follow-up.reminders-enabled", havingValue = "true", matchIfMissing = true)
public class FollowUpReminderJob {

    private static final Logger log = LoggerFactory.getLogger(FollowUpReminderJob.class);
    static final int UPCOMING_DAYS_AHEAD = 2;

    private final MongoTemplate mongoTemplate;
    private final FollowUpStore followUpStore;
    private final HospitalRepository hospitalRepository;
    private final DoctorRepository doctorRepository;
    private final HospitalEventPublisher eventPublisher;

    @Scheduled(cron = "${hospital.follow-up.reminder-cron:0 0 9-20 * * *}", zone = "Asia/Kolkata")
    public void run() {
        LocalDate today = LocalDate.now(AppointmentTokenGenerator.ZONE);
        try {
            send(today.plusDays(1), today.plusDays(UPCOMING_DAYS_AHEAD), "upcomingReminderSentAt",
                    FollowUpReminderDueEvent.UPCOMING);
            send(today, today, "dueTodayReminderSentAt", FollowUpReminderDueEvent.DUE_TODAY);
        } catch (RuntimeException e) {
            log.error("Follow-up reminder run failed; the next hourly run retries", e);
        }
    }

    // Unbooked follow-ups due in [from, to] whose reminder of this kind hasn't gone out yet.
    void send(LocalDate from, LocalDate to, String sentAtField, String reminderType) {
        List<DoctorAppointmentBooking> due = mongoTemplate.find(
                Query.query(Criteria.where("status").is(AppointmentStatus.COMPLETED)
                        .and("followUp.dueDate").gte(from.toString()).lte(to.toString())
                        .and("followUp.followUpBookingId").is(null)
                        .and("followUp." + sentAtField).is(null)
                        .and("patientEmail").nin(null, "")),
                DoctorAppointmentBooking.class);
        for (DoctorAppointmentBooking booking : due) {
            if (followUpStore.claimReminder(booking.getId(), sentAtField)) {
                eventPublisher.followUpReminderDue(toEvent(booking, reminderType));
            }
        }
        if (!due.isEmpty()) {
            log.info("Follow-up reminders ({}): {} candidate(s) due {}..{}", reminderType, due.size(), from, to);
        }
    }

    private FollowUpReminderDueEvent toEvent(DoctorAppointmentBooking booking, String reminderType) {
        FollowUpInstruction followUp = booking.getFollowUp();
        LocalDate dueDate = LocalDate.parse(followUp.getDueDate());
        Hospital hospital = hospitalRepository.findById(booking.getHospitalId()).orElse(null);
        Doctor doctor = doctorRepository.findById(booking.getDoctorId()).orElse(null);
        return new FollowUpReminderDueEvent(
                booking.getId(),
                reminderType,
                booking.getPatientId(),
                booking.getPatientName(),
                booking.getPatientEmail(),
                booking.getHospitalId(),
                hospital != null ? hospital.getHospitalName() : null,
                hospital != null && hospital.getContactDetails() != null ? hospital.getContactDetails().getPhone() : null,
                booking.getDoctorId(),
                doctor != null ? doctor.getName() : null,
                dueDate.toString(),
                FollowUpInstruction.bookableFrom(dueDate).toString(),
                FollowUpInstruction.bookableUntil(dueDate).toString(),
                followUp.getInstructions());
    }
}
