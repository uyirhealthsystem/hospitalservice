package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uyir.hospital.dto.DoctorTokenLimitResponse;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.model.DoctorTokenLimit;
import com.uyir.hospital.repository.DoctorTokenLimitRepository;
import com.uyir.hospital.service.HospitalSeatService;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DoctorTokenLimitServiceImplTest {

    @Mock
    private DoctorTokenLimitRepository repository;

    @Mock
    private HospitalSeatService hospitalSeatService;

    @InjectMocks
    private DoctorTokenLimitServiceImpl service;

    private final LocalDate today = LocalDate.now(AppointmentTokenGenerator.ZONE);

    @BeforeEach
    void setUp() {
        lenient().when(hospitalSeatService.hasSeat("h1", "d1")).thenReturn(true);
        lenient().when(repository.save(any(DoctorTokenLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private DoctorTokenLimit stored(Integer daily, Map<String, Integer> dates) {
        return DoctorTokenLimit.builder()
                .id("h1:d1")
                .hospitalId("h1")
                .doctorId("d1")
                .dailyLimit(daily)
                .dateLimits(new TreeMap<>(dates))
                .build();
    }

    @Test
    void setDailyLimit_createsRecordForSeatedDoctor() {
        when(repository.findById("h1:d1")).thenReturn(Optional.empty());

        DoctorTokenLimitResponse response = service.setDailyLimit("h1", "d1", 30);

        assertThat(response.getDailyLimit()).isEqualTo(30);
        assertThat(response.getHospitalId()).isEqualTo("h1");
        assertThat(response.getDateLimits()).isEmpty();
    }

    @Test
    void setDailyLimit_null_removesLimit() {
        when(repository.findById("h1:d1")).thenReturn(Optional.of(stored(30, Map.of())));

        assertThat(service.setDailyLimit("h1", "d1", null).getDailyLimit()).isNull();
    }

    @Test
    void setDailyLimit_unseatedDoctor_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.setDailyLimit("h1", "d2", 30)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void setDateLimit_addsOverrideAndDropsPastDates() {
        String past = today.minusDays(3).toString();
        when(repository.findById("h1:d1")).thenReturn(Optional.of(stored(30, Map.of(past, 5))));
        LocalDate date = today.plusDays(2);

        DoctorTokenLimitResponse response = service.setDateLimit("h1", "d1", date, 0);

        assertThat(response.getDateLimits()).containsExactly(Map.entry(date.toString(), 0));
    }

    @Test
    void setDateLimit_pastDate_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.setDateLimit("h1", "d1", today.minusDays(1), 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeDateLimit_missing_throwsNotFound() {
        when(repository.findById("h1:d1")).thenReturn(Optional.of(stored(30, Map.of())));

        assertThatThrownBy(() -> service.removeDateLimit("h1", "d1", today.plusDays(1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removeDateLimit_existing_fallsBackToDaily() {
        LocalDate date = today.plusDays(1);
        when(repository.findById("h1:d1")).thenReturn(Optional.of(stored(30, Map.of(date.toString(), 10))));

        assertThat(service.removeDateLimit("h1", "d1", date).getDateLimits()).isEmpty();
    }

    @Test
    void limitFor_prefersDateOverrideThenDailyThenUnlimited() {
        LocalDate overridden = today.plusDays(1);
        when(repository.findById("h1:d1")).thenReturn(Optional.of(stored(30, Map.of(overridden.toString(), 10))));
        when(repository.findById("h1:d9")).thenReturn(Optional.empty());

        assertThat(service.limitFor("h1", "d1", overridden)).isEqualTo(10);
        assertThat(service.limitFor("h1", "d1", today.plusDays(2))).isEqualTo(30);
        assertThat(service.limitFor("h1", "d9", today)).isNull();
    }
}
