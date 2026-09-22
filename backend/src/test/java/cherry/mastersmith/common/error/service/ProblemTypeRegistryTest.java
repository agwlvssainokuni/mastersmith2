/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cherry.mastersmith.common.error.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.error.domain.ProblemTypeCatalog;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProblemTypeRegistryTest {

    private static final LocalizedText TEXT = new LocalizedText("説明", "description");

    private static ProblemTypeCatalog catalog(ProblemType... types) {
        return () -> List.of(types);
    }

    @Test
    @DisplayName("collects problem types from every catalog in registration order")
    void collectsFromEveryCatalog() {
        ProblemType other = new ProblemType("ACCOUNT_LOCKED", 423, TEXT, TEXT, null);

        ProblemTypeRegistry registry = new ProblemTypeRegistry(List.of(new CommonProblemTypeCatalog(), catalog(other)));

        assertThat(registry.all()).hasSize(CommonProblemTypes.all().size() + 1).endsWith(other);
    }

    @Test
    @DisplayName("finds a problem type by code and by slug")
    void findsByCodeAndSlug() {
        ProblemTypeRegistry registry = new ProblemTypeRegistry(List.of(new CommonProblemTypeCatalog()));

        assertThat(registry.findByCode("PAYLOAD_TOO_LARGE")).contains(CommonProblemTypes.PAYLOAD_TOO_LARGE);
        assertThat(registry.findBySlug("payload-too-large")).contains(CommonProblemTypes.PAYLOAD_TOO_LARGE);
    }

    @Test
    @DisplayName("unknown code or slug returns empty")
    void unknownReturnsEmpty() {
        ProblemTypeRegistry registry = new ProblemTypeRegistry(List.of(new CommonProblemTypeCatalog()));

        assertThat(registry.findBySlug("no-such-problem")).isEmpty();
        assertThat(registry.findByCode("NO_SUCH_PROBLEM")).isEmpty();
    }

    @Test
    @DisplayName("duplicate code fails and names the duplicated code")
    void duplicateCodeFails() {
        ProblemType duplicate = new ProblemType("NOT_FOUND", 410, TEXT, TEXT, null);

        assertThatThrownBy(() -> new ProblemTypeRegistry(List.of(new CommonProblemTypeCatalog(), catalog(duplicate))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("code=[NOT_FOUND]");
    }

    @Test
    @DisplayName("an empty list of catalogs yields an empty registry")
    void emptyCatalogs() {
        ProblemTypeRegistry registry = new ProblemTypeRegistry(List.of());

        assertThat(registry.all()).isEmpty();
        assertThat(registry.findByCode("NOT_FOUND")).isEmpty();
    }

    @Test
    @DisplayName("slug derived from codes is also checked for duplicates across catalogs")
    void duplicateSlugFails() {
        ProblemType first = new ProblemType("A_B", 400, TEXT, TEXT, null);
        ProblemType second = new ProblemType("A_B", 401, TEXT, TEXT, null);

        assertThatThrownBy(() -> new ProblemTypeRegistry(List.of(catalog(first), catalog(second))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("slug=[a-b]");
    }

    @Test
    @DisplayName("every U1 problem type has Japanese and English title, description and resolution")
    void u1ProblemTypesAreBilingual() {
        assertThat(CommonProblemTypes.all())
                .extracting(ProblemType::code)
                .containsExactlyInAnyOrder(
                        "VALIDATION_FAILED",
                        "MALFORMED_REQUEST",
                        "NOT_FOUND",
                        "METHOD_NOT_ALLOWED",
                        "NOT_ACCEPTABLE",
                        "PAYLOAD_TOO_LARGE",
                        "UNSUPPORTED_MEDIA_TYPE",
                        "INTERNAL_ERROR");
        for (ProblemType type : CommonProblemTypes.all()) {
            for (LocalizedText text : List.of(type.title(), type.description(), type.resolution())) {
                assertThat(text).as(type.code()).isNotNull();
                assertThat(text.ja()).as(type.code()).isNotBlank();
                assertThat(text.en()).as(type.code()).isNotBlank();
            }
        }
    }
}
