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
                .build();
    }
}
