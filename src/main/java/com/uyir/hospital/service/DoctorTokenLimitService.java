package com.uyir.hospital.service;

import com.uyir.hospital.dto.DoctorTokenLimitResponse;
import java.time.LocalDate;
import java.util.List;

public interface DoctorTokenLimitService {

    List<DoctorTokenLimitResponse> getForHospital(String hospitalId);

    DoctorTokenLimitResponse setDailyLimit(String hospitalId, String doctorId, Integer dailyLimit);

    DoctorTokenLimitResponse setDateLimit(String hospitalId, String doctorId, LocalDate date, int limit);

    DoctorTokenLimitResponse removeDateLimit(String hospitalId, String doctorId, LocalDate date);

    // The limit that applies on that date: the date override, else the daily limit, else null (unlimited).
    Integer limitFor(String hospitalId, String doctorId, LocalDate date);
}
