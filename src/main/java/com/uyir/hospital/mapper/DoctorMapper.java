package com.uyir.hospital.mapper;

import com.uyir.hospital.dto.DoctorRequest;
import com.uyir.hospital.dto.DoctorResponse;
import com.uyir.hospital.model.Doctor;
import org.springframework.stereotype.Component;

@Component
public class DoctorMapper {

    public Doctor toEntity(DoctorRequest request) {
        return Doctor.builder()
                .name(request.getName())
                .age(request.getAge())
                .sex(request.getSex())
                .tnmcNumber(request.getTnmcNumber())
                .qualifications(request.getQualifications())
                .specialties(request.getSpecialties())
                .yearsOfExperience(request.getYearsOfExperience())
                .consultationFee(request.getConsultationFee())
                .availability(request.getAvailability())
                .engagementType(request.getEngagementType())
                .doctorCategory(request.getDoctorCategory())
                .eSignDocumentUrl(request.getESignDocumentUrl())
                .contactDetails(request.getContactDetails())
                .hospitalAssociations(request.getHospitalAssociations())
                .build();
    }

    public void updateEntity(Doctor doctor, DoctorRequest request) {
        doctor.setName(request.getName());
        doctor.setAge(request.getAge());
        doctor.setSex(request.getSex());
        doctor.setTnmcNumber(request.getTnmcNumber());
        doctor.setQualifications(request.getQualifications());
        doctor.setSpecialties(request.getSpecialties());
        doctor.setYearsOfExperience(request.getYearsOfExperience());
        doctor.setConsultationFee(request.getConsultationFee());
        doctor.setAvailability(request.getAvailability());
        doctor.setEngagementType(request.getEngagementType());
        doctor.setDoctorCategory(request.getDoctorCategory());
        doctor.setESignDocumentUrl(request.getESignDocumentUrl());
        doctor.setContactDetails(request.getContactDetails());
        doctor.setHospitalAssociations(request.getHospitalAssociations());
    }

    public DoctorResponse toResponse(Doctor doctor) {
        return DoctorResponse.builder()
                .id(doctor.getId())
                .name(doctor.getName())
                .age(doctor.getAge())
                .sex(doctor.getSex())
                .tnmcNumber(doctor.getTnmcNumber())
                .qualifications(doctor.getQualifications())
                .specialties(doctor.getSpecialties())
                .yearsOfExperience(doctor.getYearsOfExperience())
                .consultationFee(doctor.getConsultationFee())
                .availability(doctor.getAvailability())
                .engagementType(doctor.getEngagementType())
                .doctorCategory(doctor.getDoctorCategory())
                .eSignDocumentUrl(doctor.getESignDocumentUrl())
                .contactDetails(doctor.getContactDetails())
                .hospitalAssociations(doctor.getHospitalAssociations())
                .currentHospitalId(doctor.getCurrentHospitalId())
                .checkedInAt(doctor.getCheckedInAt())
                .active(doctor.isActive())
                .createdAt(doctor.getCreatedAt())
                .updatedAt(doctor.getUpdatedAt())
                .build();
    }
}
