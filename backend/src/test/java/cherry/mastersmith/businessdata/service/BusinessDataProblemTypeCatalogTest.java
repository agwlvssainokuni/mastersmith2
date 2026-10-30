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
package cherry.mastersmith.businessdata.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.domain.BusinessDataProblemTypes;
import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.error.domain.ProblemType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 業務データの問題の種類（BR1.9）の単体テスト。 */
class BusinessDataProblemTypeCatalogTest {

    private final List<ProblemType> types = new BusinessDataProblemTypeCatalog().problemTypes();

    @Test
    @DisplayName("the catalog has the eight read codes and the five write codes with one status each")
    void codesAndStatuses() {
        Map<String, Integer> statuses =
                types.stream().collect(Collectors.toMap(ProblemType::code, ProblemType::status));

        assertThat(statuses)
                .containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                        Map.entry("BUSINESS_DSL_NOT_APPLIED", 404),
                        Map.entry("BUSINESS_TABLE_NOT_FOUND", 404),
                        Map.entry("BUSINESS_ROW_NOT_FOUND", 404),
                        Map.entry("BUSINESS_ROW_OPERATION_NOT_ALLOWED", 409),
                        Map.entry("BUSINESS_ROW_NOT_UNIQUE", 409),
                        Map.entry("BUSINESS_TARGET_DB_MISMATCH", 409),
                        Map.entry("BUSINESS_TARGET_DB_UNAVAILABLE", 503),
                        Map.entry("BUSINESS_TARGET_DB_TIMEOUT", 503),
                        // U2（Intent 261016-business-data-views の B4、BR1.9）が足した書き込みの code（どれも 409）
                        Map.entry("BUSINESS_DEFINITION_CHANGED", 409),
                        Map.entry("BUSINESS_WRITE_NOT_CONFIGURED", 409),
                        Map.entry("BUSINESS_DUPLICATE_VALUE", 409),
                        Map.entry("BUSINESS_REFERENCE_NOT_FOUND", 409),
                        Map.entry("BUSINESS_SAVE_REJECTED", 409)));
    }

    @Test
    @DisplayName("the catalog does not redefine the common codes")
    void noCommonCodes() {
        assertThat(types)
                .extracting(ProblemType::code)
                .doesNotContain("ACCESS_DENIED", "VALIDATION_FAILED", CommonProblemTypes.VALIDATION_FAILED.code());
    }

    @Test
    @DisplayName("the codes do not repeat")
    void noDuplicates() {
        assertThat(types).extracting(ProblemType::code).doesNotHaveDuplicates();
        assertThat(types).extracting(ProblemType::slug).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("every type has a title and a description in Japanese and English")
    void texts() {
        assertThat(types).allSatisfy(type -> {
            assertThat(type.title().ja()).isNotBlank();
            assertThat(type.title().en()).isNotBlank();
            assertThat(type.description().ja()).isNotBlank();
            assertThat(type.description().en()).isNotBlank();
        });
    }

    @Test
    @DisplayName("the catalog returns the domain list as it is")
    void sameAsTheDomainList() {
        assertThat(types).containsExactlyElementsOf(BusinessDataProblemTypes.all());
        assertThat(BusinessDataProblemTypes.BUSINESS_ROW_OPERATION_NOT_ALLOWED.resolution())
                .isNull();
    }

    @Test
    @DisplayName("other table references add no code: still 13 codes with the same statuses (T-C3, NFR6.1)")
    void referencesAddNoCode() {
        // Intent 261029-reference-lookup-cleanup の U1 は新しい code を作らず、参照先の失敗を既存の code に写す（BR1.5・BR1.6）。
        assertThat(types).hasSize(13);
        assertThat(types.stream().collect(Collectors.groupingBy(ProblemType::status, Collectors.counting())))
                .containsExactlyInAnyOrderEntriesOf(Map.of(404, 3L, 409, 8L, 503, 2L));
    }
}
