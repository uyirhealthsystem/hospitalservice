package com.uyir.hospital.controller;

import com.uyir.hospital.catalog.SpecialtyCatalog;
import com.uyir.hospital.catalog.SpecialtyCategory;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hospital/specialties")
@RequiredArgsConstructor
@Tag(name = "Specialties")
public class SpecialtyController {

    private final SpecialtyCatalog specialtyCatalog;

    // Public: the doctor specialty master list, grouped by category, for the doctor form's specialty picker
    // and for search filters. A doctor's `specialties` must come from this list.
    @GetMapping
    public List<SpecialtyCategory> getAll() {
        return specialtyCatalog.getCategories();
    }
}
