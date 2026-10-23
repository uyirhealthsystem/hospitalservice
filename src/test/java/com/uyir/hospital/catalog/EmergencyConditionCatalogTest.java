package com.uyir.hospital.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

// Runs against the real emergency-conditions.json.
class EmergencyConditionCatalogTest {

    private final EmergencyConditionCatalog catalog = new EmergencyConditionCatalog(JsonMapper.builder().build());

    @Test
    void loadsThe16ConditionsInOrder() {
        List<EmergencyCondition> conditions = catalog.getConditions();

        assertThat(conditions).hasSize(16);
        assertThat(conditions.getFirst()).isEqualTo(new EmergencyCondition("EMG001", "Chest Pain / Sweating / Palpitation", "❤️"));
        assertThat(conditions.getLast()).isEqualTo(new EmergencyCondition("EMG016", "Other Emergency", "❗"));
        assertThat(conditions).extracting(EmergencyCondition::code).doesNotHaveDuplicates();
    }

    @Test
    void requireName_byCodeOrNameIgnoringCaseAndSpaces() {
        assertThat(catalog.requireName("EMG012")).isEqualTo("Road Traffic Accident");
        assertThat(catalog.requireName("emg012")).isEqualTo("Road Traffic Accident");
        assertThat(catalog.requireName(" road traffic accident ")).isEqualTo("Road Traffic Accident");
    }

    @Test
    void requireName_unknownOrNull_isRejected() {
        assertThatThrownBy(() -> catalog.requireName("Heart Attack"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("emergencyType")
                .hasMessageContaining("Heart Attack");
        assertThatThrownBy(() -> catalog.requireName(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalize_officialNamesInOrderWithoutDuplicates() {
        assertThat(catalog.normalize("f", List.of("EMG010", "Fall / Fracture", "EMG007")))
                .containsExactly("Fall / Fracture", "Child Emergency");
    }

    @Test
    void normalize_nullStaysNull_emptyStaysEmpty() {
        assertThat(catalog.normalize("f", null)).isNull();
        assertThat(catalog.normalize("f", List.of())).isEmpty();
    }

    @Test
    void normalize_reportsEveryUnknownUnderTheFieldName() {
        assertThatThrownBy(() -> catalog.normalize("emergencyConditionsHandled", List.of("Heart Attack", "EMG001", "XYZ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("emergencyConditionsHandled: unknown emergency conditions [Heart Attack, XYZ]");
    }
}
