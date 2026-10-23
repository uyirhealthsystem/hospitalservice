package com.uyir.hospital.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

// The specialty master list: a doctor's specialties and a hospital's departments, for appointment booking
// and doctor search (emergencies use EmergencyConditionCatalog instead). Picked from the list rather than typed,
// so there is one spelling per specialty. Loaded once from specialties.json; edit that file (and redeploy) to
// add or rename a specialty.
//
// A doctor's `specialties` are stored as the official NAMES (what search and SOS already match on);
// the request may send a code ("CAR001") or a name in any case, and both are normalized to the name.
@Component
public class SpecialtyCatalog {

    static final String RESOURCE = "specialties.json";

    private final List<SpecialtyCategory> categories;
    // lower-cased code or name -> official name
    private final Map<String, String> canonicalNames = new HashMap<>();

    public SpecialtyCatalog(JsonMapper jsonMapper) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            this.categories = List.copyOf(jsonMapper.readValue(in, new TypeReference<List<SpecialtyCategory>>() {}));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read " + RESOURCE, ex);
        }

        Set<String> codes = new LinkedHashSet<>();
        for (SpecialtyCategory category : categories) {
            for (Specialty specialty : category.specialties()) {
                if (isBlank(specialty.code()) || isBlank(specialty.name())) {
                    throw new IllegalStateException(RESOURCE + ": blank code or name in category '" + category.category() + "'");
                }
                if (!codes.add(specialty.code())) {
                    throw new IllegalStateException(RESOURCE + ": duplicate specialty code '" + specialty.code() + "'");
                }
                canonicalNames.put(key(specialty.code()), specialty.name());
                // The same name may sit under two categories (Hemato-Oncology: ONC005 and HEM003) - same specialty.
                canonicalNames.put(key(specialty.name()), specialty.name());
            }
        }
    }

    public List<SpecialtyCategory> getCategories() {
        return categories;
    }

    // Code or name (any case, surrounding spaces ignored) -> the official name.
    public Optional<String> resolve(String codeOrName) {
        return codeOrName == null ? Optional.empty() : Optional.ofNullable(canonicalNames.get(key(codeOrName)));
    }

    // Validates values against the master list and returns them as official names, in the order given,
    // without duplicates. null stays null (the fields using this are optional). Every unknown entry is
    // reported at once, under the request field's name, so the form can flag them all.
    public List<String> normalize(String field, List<String> codesOrNames) {
        if (codesOrNames == null) {
            return null;
        }
        Set<String> names = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String value : codesOrNames) {
            resolve(value).ifPresentOrElse(names::add, () -> unknown.add(value));
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(field + ": unknown specialt" + (unknown.size() == 1 ? "y " : "ies ") + unknown
                    + ". Choose from GET /api/v1/hospital/specialties (send the code or the exact name).");
        }
        return List.copyOf(names);
    }

    private static String key(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
