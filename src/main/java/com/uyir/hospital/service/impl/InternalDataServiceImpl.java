package com.uyir.hospital.service.impl;

import com.uyir.hospital.dto.DoctorHospitalStatus;
import com.uyir.hospital.dto.DoctorResponse;
import com.uyir.hospital.dto.HospitalResponse;
import com.uyir.hospital.dto.InternalDataRequest;
import com.uyir.hospital.dto.InternalDataResponse;
import com.uyir.hospital.dto.InternalDataSection;
import com.uyir.hospital.mapper.DoctorMapper;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.embedded.HospitalAssociation;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.service.DoctorService;
import com.uyir.hospital.service.HospitalSeatService;
import com.uyir.hospital.service.HospitalService;
import com.uyir.hospital.service.InternalDataService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class InternalDataServiceImpl implements InternalDataService {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {
    };

    private final HospitalService hospitalService;
    private final DoctorService doctorService;
    private final DoctorRepository doctorRepository;
    private final DoctorMapper doctorMapper;
    private final HospitalSeatService hospitalSeatService;
    private final JsonMapper jsonMapper;

    @Override
    public InternalDataResponse lookup(InternalDataRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        String hospitalId = trimToNull(request.getHospitalId());
        String doctorId = trimToNull(request.getDoctorId());
        if (hospitalId == null && doctorId == null) {
            throw new IllegalArgumentException("hospitalId or doctorId is required");
        }
        Set<InternalDataSection> sections = resolveSections(request.getInclude(), hospitalId, doctorId);

        // Loading both up front also turns an unknown id into a 404 whatever was asked for.
        HospitalResponse hospital = hospitalId != null ? hospitalService.getById(hospitalId) : null;
        DoctorResponse doctor = doctorId != null ? doctorService.getById(doctorId) : null;

        InternalDataResponse.InternalDataResponseBuilder out = InternalDataResponse.builder()
                .hospitalId(hospitalId)
                .doctorId(doctorId);
        List<String> doctorFields = request.getDoctorFields();

        if (sections.contains(InternalDataSection.HOSPITAL)) {
            out.hospital(project(hospital, request.getHospitalFields(), "hospitalFields"));
        }
        if (sections.contains(InternalDataSection.DOCTOR)) {
            out.doctor(project(doctor, doctorFields, "doctorFields"));
        }
        if (sections.contains(InternalDataSection.DOCTOR_STATUS)) {
            out.doctorStatus(status(hospital, doctor, hospitalSeatService.hasSeat(hospitalId, doctorId)));
        }
        if (sections.contains(InternalDataSection.SEATED_DOCTORS)) {
            List<Doctor> seated = new ArrayList<>();
            doctorRepository.findAllById(hospitalSeatService.seatedDoctorIds(hospitalId)).forEach(seated::add);
            out.seatedDoctors(projectDoctors(seated, doctorFields));
        }
        if (sections.contains(InternalDataSection.ON_DUTY_DOCTORS)) {
            out.onDutyDoctors(projectDoctors(
                    doctorRepository.findByCurrentHospitalIdAndActiveTrue(hospitalId), doctorFields));
        }
        if (sections.contains(InternalDataSection.SEATS)) {
            out.seats(hospitalSeatService.getSeats(hospitalId));
        }
        return out.build();
    }

    // No include = HOSPITAL and/or DOCTOR for the ids given, plus DOCTOR_STATUS when both are given.
    static Set<InternalDataSection> resolveSections(
            List<InternalDataSection> include, String hospitalId, String doctorId) {
        Set<InternalDataSection> sections = EnumSet.noneOf(InternalDataSection.class);
        if (include == null || include.stream().allMatch(Objects::isNull)) {
            if (hospitalId != null) {
                sections.add(InternalDataSection.HOSPITAL);
            }
            if (doctorId != null) {
                sections.add(InternalDataSection.DOCTOR);
            }
            if (hospitalId != null && doctorId != null) {
                sections.add(InternalDataSection.DOCTOR_STATUS);
            }
            return sections;
        }
        include.stream().filter(Objects::nonNull).forEach(sections::add);
        for (InternalDataSection section : sections) {
            boolean needsHospital = section != InternalDataSection.DOCTOR;
            boolean needsDoctor = section == InternalDataSection.DOCTOR
                    || section == InternalDataSection.DOCTOR_STATUS;
            if (needsHospital && hospitalId == null) {
                throw new IllegalArgumentException(section + " needs hospitalId");
            }
            if (needsDoctor && doctorId == null) {
                throw new IllegalArgumentException(section + " needs doctorId");
            }
        }
        return sections;
    }

    private static DoctorHospitalStatus status(HospitalResponse hospital, DoctorResponse doctor, boolean seated) {
        HospitalAssociation association = doctor.getHospitalAssociations() == null ? null
                : doctor.getHospitalAssociations().stream()
                        .filter(a -> hospital.getId().equals(a.getHospitalId()))
                        .findFirst()
                        .orElse(null);
        return DoctorHospitalStatus.builder()
                .hospitalActive(hospital.isActive())
                .doctorActive(doctor.isActive())
                .seated(seated)
                .associated(association != null)
                .associationActive(association != null && association.isActive())
                .department(association != null ? association.getDepartment() : null)
                .consultationFee(association != null && association.getConsultationFee() != null
                        ? association.getConsultationFee() : doctor.getConsultationFee())
                .checkedInHere(hospital.getId().equals(doctor.getCurrentHospitalId()))
                .currentHospitalId(doctor.getCurrentHospitalId())
                .checkedInAt(doctor.getCheckedInAt())
                .bookable(seated && hospital.isActive() && doctor.isActive())
                .build();
    }

    private List<Map<String, Object>> projectDoctors(List<Doctor> doctors, List<String> fields) {
        return doctors.stream()
                .sorted(Comparator.comparing(Doctor::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(doctorMapper::toResponse)
                .map(d -> project(d, fields, "doctorFields"))
                .toList();
    }

    // The object as the public API serializes it, cut down to the requested fields. A dotted path
    // ("address.district") keeps just that nested value. An unknown top-level field is a 400 listing the valid ones.
    private Map<String, Object> project(Object source, List<String> fields, String param) {
        Map<String, Object> full = jsonMapper.convertValue(source, MAP);
        List<String> wanted = fields == null ? List.of()
                : fields.stream().filter(Objects::nonNull).map(String::trim).filter(f -> !f.isEmpty()).toList();
        if (wanted.isEmpty()) {
            return full;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (String path : wanted) {
            String[] parts = path.split("\\.");
            if (!full.containsKey(parts[0])) {
                throw new IllegalArgumentException("Unknown field '" + parts[0] + "' in " + param
                        + "; valid fields: " + new TreeSet<>(full.keySet()));
            }
            copyPath(full, result, parts, 0);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void copyPath(Map<String, Object> from, Map<String, Object> to, String[] parts, int i) {
        String key = parts[i];
        if (!from.containsKey(key)) {
            return;
        }
        Object value = from.get(key);
        if (i == parts.length - 1 || !(value instanceof Map)) {
            to.put(key, value);
            return;
        }
        Object existing = to.get(key);
        if (existing != null && !(existing instanceof Map)) {
            return; // the whole value was already asked for
        }
        Map<String, Object> child = existing != null ? (Map<String, Object>) existing : new LinkedHashMap<>();
        to.put(key, child);
        copyPath((Map<String, Object>) value, child, parts, i + 1);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
