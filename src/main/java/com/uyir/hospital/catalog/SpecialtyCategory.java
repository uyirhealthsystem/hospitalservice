package com.uyir.hospital.catalog;

import java.util.List;

// A group in the specialty master list, e.g. "Heart & Vascular" -> Cardiology, Interventional Cardiology, ...
public record SpecialtyCategory(String category, List<Specialty> specialties) {
}
