package com.uyir.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkInvoicePaidRequest {

    // e.g. the bank transfer UTR or cheque number
    @NotBlank
    @Size(max = 200)
    private String reference;
}
