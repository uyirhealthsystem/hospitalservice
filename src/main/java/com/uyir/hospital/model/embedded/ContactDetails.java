package com.uyir.hospital.model.embedded;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactDetails {

    @NotBlank
    private String phone;

    private String alternatePhone;

    @Email
    private String email;

    private String emergencyHotline;
    private String website;
}
