package com.uyir.hospital.service.impl;

import com.uyir.hospital.dto.DoctorTokenLimitResponse;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.model.DoctorTokenLimit;
import com.uyir.hospital.repository.DoctorTokenLimitRepository;
import com.uyir.hospital.service.DoctorTokenLimitService;
import com.uyir.hospital.service.HospitalSeatService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DoctorTokenLimitServiceImpl implements DoctorTokenLimitService {

    private final DoctorTokenLimitRepository repository;
    private final HospitalSeatService hospitalSeatService;

    @Override
    public List<DoctorTokenLimitResponse> getForHospital(String hospitalId) {
        return repository.findByHospitalIdOrderByDoctorIdAsc(hospitalId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public DoctorTokenLimitResponse setDailyLimit(String hospitalId, String doctorId, Integer dailyLimit) {
        requireSeated(hospitalId, doctorId);
        DoctorTokenLimit limit = loadOrNew(hospitalId, doctorId);
        limit.setDailyLimit(dailyLimit);
        return save(limit);
    }

    @Override
    public DoctorTokenLimitResponse setDateLimit(String hospitalId, String doctorId, LocalDate date, int value) {
        requireSeated(hospitalId, doctorId);
        if (date.isBefore(today())) {
            throw new IllegalArgumentException("Cannot set a token limit for a past date (" + date + ")");
        }
        DoctorTokenLimit limit = loadOrNew(hospitalId, doctorId);
        limit.getDateLimits().put(date.toString(), value);
        return save(limit);
    }

    @Override
    public DoctorTokenLimitResponse removeDateLimit(String hospitalId, String doctorId, LocalDate date) {
        DoctorTokenLimit limit = repository.findById(DoctorTokenLimit.idFor(hospitalId, doctorId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No token limits set for doctor '" + doctorId + "' at this hospital"));
        if (limit.getDateLimits() == null || limit.getDateLimits().remove(date.toString()) == null) {
            throw new ResourceNotFoundException("No token limit set for doctor '" + doctorId + "' on " + date);
        }
        return save(limit);
    }

    @Override
    public Integer limitFor(String hospitalId, String doctorId, LocalDate date) {
        return repository.findById(DoctorTokenLimit.idFor(hospitalId, doctorId))
                .map(limit -> {
                    Map<String, Integer> dates = limit.getDateLimits();
                    Integer override = dates == null ? null : dates.get(date.toString());
                    return override != null ? override : limit.getDailyLimit();
                })
                .orElse(null);
    }

    private void requireSeated(String hospitalId, String doctorId) {
        if (!hospitalSeatService.hasSeat(hospitalId, doctorId)) {
            throw new IllegalArgumentException(
                    "Doctor '" + doctorId + "' does not hold a seat at hospital '" + hospitalId + "'");
        }
    }

    private DoctorTokenLimit loadOrNew(String hospitalId, String doctorId) {
        DoctorTokenLimit limit = repository.findById(DoctorTokenLimit.idFor(hospitalId, doctorId))
                .orElseGet(() -> DoctorTokenLimit.builder()
                        .id(DoctorTokenLimit.idFor(hospitalId, doctorId))
                        .hospitalId(hospitalId)
                        .doctorId(doctorId)
                        .build());
        if (limit.getDateLimits() == null) {
            limit.setDateLimits(new TreeMap<>());
        } else {
            limit.setDateLimits(new TreeMap<>(limit.getDateLimits()));
        }
        return limit;
    }

    // Past date overrides no longer matter; drop them on every write so the map stays small.
    private DoctorTokenLimitResponse save(DoctorTokenLimit limit) {
        String today = today().toString();
        limit.getDateLimits().keySet().removeIf(day -> day.compareTo(today) < 0);
        limit.setUpdatedAt(Instant.now());
        return toResponse(repository.save(limit));
    }

    private DoctorTokenLimitResponse toResponse(DoctorTokenLimit limit) {
        String today = today().toString();
        Map<String, Integer> upcoming = new TreeMap<>();
        if (limit.getDateLimits() != null) {
            limit.getDateLimits().forEach((day, value) -> {
                if (day.compareTo(today) >= 0) {
                    upcoming.put(day, value);
                }
            });
        }
        return DoctorTokenLimitResponse.builder()
                .hospitalId(limit.getHospitalId())
                .doctorId(limit.getDoctorId())
                .dailyLimit(limit.getDailyLimit())
                .dateLimits(upcoming)
                .updatedAt(limit.getUpdatedAt())
                .build();
    }

    private static LocalDate today() {
        return LocalDate.now(AppointmentTokenGenerator.ZONE);
    }
}
