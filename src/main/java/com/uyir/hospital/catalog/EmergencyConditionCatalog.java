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

// The 16 emergency conditions shown to a patient in SOS. Loaded once from emergency-conditions.json.
// Emergency booking only - the specialty / department list (SpecialtyCatalog) is for appointments.
//
// Three things must use exactly these names, all stored as the official name:
//   - the patient's emergencyType (SOS search and emergency booking)
//   - the hospital's emergencyServices.specialtyEmergencyConditionsHandled
//   - each doctor's emergencyConditionsHandled
// A hospital is offered for an emergency only when it and a doctor on duty there both handle it
// (see EmergencyCoverage).
@Component
public class EmergencyConditionCatalog {

    static final String RESOURCE = "emergency-conditions.json";
    private static final String LIST_HINT = "Choose from GET /api/v1/hospital/emergency-conditions (send the code or the exact name).";

    private final List<EmergencyCondition> conditions;
    // lower-cased code or name -> condition
    private final Map<String, EmergencyCondition> lookup = new HashMap<>();

    public EmergencyConditionCatalog(JsonMapper jsonMapper) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            this.conditions = List.copyOf(jsonMapper.readValue(in, new TypeReference<List<EmergencyCondition>>() {}));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read " + RESOURCE, ex);
        }
        for (EmergencyCondition condition : conditions) {
            if (isBlank(condition.code()) || isBlank(condition.name())) {
                throw new IllegalStateException(RESOURCE + ": blank code or name");
            }
            if (lookup.put(key(condition.code()), condition) != null || lookup.put(key(condition.name()), condition) != null) {
                throw new IllegalStateException(RESOURCE + ": duplicate code or name '" + condition.code() + "'");
            }
        }
    }

    public List<EmergencyCondition> getConditions() {
        return conditions;
    }

    public Optional<EmergencyCondition> resolve(String codeOrName) {
        return codeOrName == null ? Optional.empty() : Optional.ofNullable(lookup.get(key(codeOrName)));
    }

    // Patient's emergencyType (code or name) -> the official name; 400 if it isn't one of the 16.
    public String requireName(String emergencyType) {
        return resolve(emergencyType)
                .orElseThrow(() -> new IllegalArgumentException(
                        "emergencyType: unknown emergency condition '" + emergencyType + "'. " + LIST_HINT))
                .name();
    }

    // A hospital's or doctor's handled conditions -> official names, in the order given, without duplicates.
    // null stays null (the fields are optional). Every unknown entry is reported at once, under the field's name.
    public List<String> normalize(String field, List<String> codesOrNames) {
        if (codesOrNames == null) {
            return null;
        }
        Set<String> names = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String value : codesOrNames) {
            resolve(value).ifPresentOrElse(condition -> names.add(condition.name()), () -> unknown.add(value));
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(field + ": unknown emergency condition" + (unknown.size() == 1 ? " " : "s ")
                    + unknown + ". " + LIST_HINT);
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
