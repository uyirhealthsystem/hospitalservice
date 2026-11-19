package com.uyir.hospital.mapper;

import com.uyir.hospital.dto.HospitalRequest;
import com.uyir.hospital.dto.HospitalResponse;
import com.uyir.hospital.model.Hospital;
import org.springframework.stereotype.Component;

@Component
public class HospitalMapper {

    public Hospital toEntity(HospitalRequest request) {
        return Hospital.builder()
                .hospitalName(request.getHospitalName())
                .registrationNumber(request.getRegistrationNumber())
                .ownershipType(request.getOwnershipType())
                .hospitalType(request.getHospitalType())
                .address(request.getAddress())
                .contactDetails(request.getContactDetails())
                .facilities(request.getFacilities())
                .emergencyServices(request.getEmergencyServices())
                .staffDetails(request.getStaffDetails())
                .operations(request.getOperations())
                .surgicalNetwork(request.getSurgicalNetwork())
                .build();
    }

    public void updateEntity(Hospital hospital, HospitalRequest request) {
        hospital.setHospitalName(request.getHospitalName());
        hospital.setRegistrationNumber(request.getRegistrationNumber());
        hospital.setOwnershipType(request.getOwnershipType());
        hospital.setHospitalType(request.getHospitalType());
        hospital.setAddress(request.getAddress());
        hospital.setContactDetails(request.getContactDetails());
        hospital.setFacilities(request.getFacilities());
        hospital.setEmergencyServices(request.getEmergencyServices());
        hospital.setStaffDetails(request.getStaffDetails());
        hospital.setOperations(request.getOperations());
        hospital.setSurgicalNetwork(request.getSurgicalNetwork());
    }

    public HospitalResponse toResponse(Hospital hospital) {
        return HospitalResponse.builder()
                .id(hospital.getId())
                .userId(hospital.getUserId())
                .hospitalName(hospital.getHospitalName())
                .registrationNumber(hospital.getRegistrationNumber())
                .ownershipType(hospital.getOwnershipType())
                .hospitalType(hospital.getHospitalType())
                .address(hospital.getAddress())
                .contactDetails(hospital.getContactDetails())
                .facilities(hospital.getFacilities())
                .emergencyServices(hospital.getEmergencyServices())
                .staffDetails(hospital.getStaffDetails())
                .operations(hospital.getOperations())
                .surgicalNetwork(hospital.getSurgicalNetwork())
                .active(hospital.isActive())
                .createdAt(hospital.getCreatedAt())
                .updatedAt(hospital.getUpdatedAt())
                .onboardedByProUserId(hospital.getOnboardedByProUserId())
                .claimed(hospital.getUserId() != null)
                .claimCodeSentTo(claimPending(hospital) ? maskEmail(email(hospital)) : null)
                .claimCodeExpiresAt(claimPending(hospital) ? hospital.getClaimCodeExpiresAt() : null)
                .build();
    }

    private static boolean claimPending(Hospital hospital) {
        return hospital.getUserId() == null && hospital.getClaimCodeHash() != null;
    }

    private static String email(Hospital hospital) {
        return hospital.getContactDetails() == null ? null : hospital.getContactDetails().getEmail();
    }

    // "admin@cityhospital.in" -> "ad***@cityhospital.in"
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + email.substring(at);
    }
}
