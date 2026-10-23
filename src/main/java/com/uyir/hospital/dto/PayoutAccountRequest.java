package com.uyir.hospital.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// The hospital's legal and bank details for Razorpay Route. Sent in full every time: steps already
// done at Razorpay are skipped, and once the Razorpay account exists only bankAccount can still
// change (business details are then changed through Razorpay support).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutAccountRequest {

    private static final String PAN = "[A-Z]{5}[0-9]{4}[A-Z]";

    // As registered with the government (PAN / GST certificate), not the display name.
    @NotBlank
    @Size(min = 4, max = 200)
    private String legalBusinessName;

    @NotBlank
    @Pattern(regexp = "individual|proprietorship|partnership|private_limited|public_limited|llp|ngo|trust|society"
            + "|not_yet_registered|educational_institutes",
            message = "must be one of individual, proprietorship, partnership, private_limited, public_limited, llp, "
                    + "ngo, trust, society, not_yet_registered, educational_institutes")
    private String businessType;

    @NotBlank
    @Size(min = 4, max = 120)
    private String contactName;

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    @NotBlank
    @Pattern(regexp = "[0-9]{10}", message = "must be a 10-digit mobile number")
    private String phone;

    @Valid
    @NotNull
    private RegisteredAddress address;

    // The business PAN.
    @NotBlank
    @Pattern(regexp = PAN, message = "must be a valid PAN, e.g. AAACS1234C")
    private String pan;

    @Pattern(regexp = "[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]", message = "must be a valid GSTIN")
    private String gst;

    @Valid
    @NotNull
    private Stakeholder stakeholder;

    @Valid
    @NotNull
    private BankAccount bankAccount;

    // The hospital accepts Razorpay's Route terms.
    @NotNull
    @AssertTrue(message = "must be true: Razorpay's Route terms must be accepted")
    private Boolean tncAccepted;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisteredAddress {

        @NotBlank
        @Size(max = 100)
        private String street1;

        @Size(max = 100)
        private String street2;

        @NotBlank
        @Size(max = 100)
        private String city;

        // Full state name, e.g. "Tamil Nadu".
        @NotBlank
        @Size(max = 100)
        private String state;

        @NotBlank
        @Pattern(regexp = "[1-9][0-9]{5}", message = "must be a 6-digit PIN code")
        private String postalCode;
    }

    // The owner or authorised signatory.
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stakeholder {

        @NotBlank
        @Size(max = 120)
        private String name;

        @NotBlank
        @Email
        @Size(max = 254)
        private String email;

        @Pattern(regexp = "[0-9]{10}", message = "must be a 10-digit mobile number")
        private String phone;

        // Their personal PAN.
        @NotBlank
        @Pattern(regexp = PAN, message = "must be a valid PAN, e.g. ABCPK1234D")
        private String pan;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankAccount {

        @NotBlank
        @Pattern(regexp = "[0-9]{9,18}", message = "must be 9 to 18 digits")
        private String accountNumber;

        @NotBlank
        @Pattern(regexp = "[A-Z]{4}0[A-Z0-9]{6}", message = "must be a valid IFSC, e.g. HDFC0000317")
        private String ifscCode;

        // Name on the bank account.
        @NotBlank
        @Size(max = 120)
        private String beneficiaryName;

        @Override
        public String toString() {
            return "BankAccount(ifscCode=" + ifscCode + ")";
        }
    }

    // Never log the bank account number.
    @Override
    public String toString() {
        return "PayoutAccountRequest(legalBusinessName=" + legalBusinessName + ", businessType=" + businessType + ")";
    }
}
