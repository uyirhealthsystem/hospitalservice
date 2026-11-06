package com.uyir.hospital.controller;

import com.uyir.hospital.dto.InternalDataRequest;
import com.uyir.hospital.dto.InternalDataResponse;
import com.uyir.hospital.service.InternalDataService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Service-to-service only (not for the gateway's user traffic). No authentication: the caller says which
// data it needs for a hospitalId and/or doctorId and gets just that.
@RestController
@RequestMapping("/api/v1/hospital/internal")
@RequiredArgsConstructor
@Tag(name = "Internal")
public class InternalDataController {

    private final InternalDataService internalDataService;

    @PostMapping("/lookup")
    public InternalDataResponse lookup(@RequestBody InternalDataRequest request) {
        return internalDataService.lookup(request);
    }
}
