package com.uyir.hospital.controller;

import com.uyir.hospital.dto.InternalDataRequest;
import com.uyir.hospital.dto.InternalDataResponse;
import com.uyir.hospital.security.InternalApiKeyGuard;
import com.uyir.hospital.service.InternalDataService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Service-to-service only (not for the gateway's user traffic): authenticated by X-Internal-Api-Key,
// no user headers. The caller says which data it needs for a hospitalId and/or doctorId and gets just that.
@RestController
@RequestMapping("/api/v1/hospital/internal")
@RequiredArgsConstructor
@Tag(name = "Internal")
public class InternalDataController {

    private final InternalDataService internalDataService;
    private final InternalApiKeyGuard internalApiKeyGuard;

    @PostMapping("/lookup")
    public InternalDataResponse lookup(
            @RequestHeader(value = InternalApiKeyGuard.HEADER, required = false) String apiKey,
            @RequestBody InternalDataRequest request) {
        internalApiKeyGuard.check(apiKey);
        return internalDataService.lookup(request);
    }
}
