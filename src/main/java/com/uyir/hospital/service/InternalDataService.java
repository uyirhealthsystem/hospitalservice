package com.uyir.hospital.service;

import com.uyir.hospital.dto.InternalDataRequest;
import com.uyir.hospital.dto.InternalDataResponse;

// One lookup for other Uyir services: hospital and/or doctor data by id, only the sections and fields asked for.
public interface InternalDataService {

    InternalDataResponse lookup(InternalDataRequest request);
}
