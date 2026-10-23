package com.uyir.hospital.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

// Runs against the real specialties.json.
class SpecialtyCatalogTest {

    private final SpecialtyCatalog catalog = new SpecialtyCatalog(JsonMapper.builder().build());

    @Test
    void loadsTheFullMasterList() {
        List<SpecialtyCategory> categories = catalog.getCategories();

        assertThat(categories).hasSize(30);
        assertThat(categories.stream().mapToInt(c -> c.specialties().size()).sum()).isEqualTo(131);
        assertThat(categories.getFirst().category()).isEqualTo("General Medicine");
        assertThat(categories.getFirst().specialties().getFirst()).isEqualTo(new Specialty("GEN001", "General Physician"));
        assertThat(categories.getLast().category()).isEqualTo("Transplant Medicine");
    }

    @Test
    void resolve_byCodeOrName_ignoringCaseAndSpaces() {
        assertThat(catalog.resolve("CAR001")).contains("Cardiology");
        assertThat(catalog.resolve("car001")).contains("Cardiology");
        assertThat(catalog.resolve("  obstetrics & gynaecology ")).contains("Obstetrics & Gynaecology");
        assertThat(catalog.resolve("GENE002")).contains("Genetic Counselling");
    }

    @Test
    void resolve_unknownOrNull_isEmpty() {
        assertThat(catalog.resolve("Heart Doctor")).isEmpty();
        assertThat(catalog.resolve("")).isEmpty();
        assertThat(catalog.resolve(null)).isEmpty();
    }

    @Test
    void sameNameUnderTwoCategories_resolvesToOneSpecialty() {
        assertThat(catalog.resolve("ONC005")).contains("Hemato-Oncology");
        assertThat(catalog.resolve("HEM003")).contains("Hemato-Oncology");
        assertThat(catalog.normalize("specialties", List.of("ONC005", "HEM003"))).containsExactly("Hemato-Oncology");
    }

    @Test
    void normalize_nullStaysNull_emptyStaysEmpty() {
        assertThat(catalog.normalize("specialties", null)).isNull();
        assertThat(catalog.normalize("specialties", List.of())).isEmpty();
    }

    @Test
    void normalize_blankOrNullEntry_isRejected() {
        assertThatThrownBy(() -> catalog.normalize("specialties", Arrays.asList("Cardiology", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.normalize("specialties", List.of(" ")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
