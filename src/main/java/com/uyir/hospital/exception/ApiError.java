package com.uyir.hospital.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiError {

    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    // Machine-readable reason the UI can switch on (e.g. ANALYTICS_PIN_NOT_SET). Omitted when there is none.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String code;
    private String path;
    private List<String> details;
}
