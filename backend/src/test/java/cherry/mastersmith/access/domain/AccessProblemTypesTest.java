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
package cherry.mastersmith.access.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import cherry.mastersmith.auth.domain.AuthProblemTypes;
import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.error.domain.ProblemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccessProblemTypesTest {

    @Test
    @DisplayName("two problem types with their codes and status codes")
    void codesAndStatuses() {
        assertThat(AccessProblemTypes.all())
                .extracting(ProblemType::code, ProblemType::status)
                .containsExactly(tuple("ACCESS_DENIED", 403), tuple("REQUEST_REJECTED", 400));
    }

    @Test
    @DisplayName("every problem type has Japanese and English texts including a resolution")
    void bilingual() {
        assertThat(AccessProblemTypes.all()).allSatisfy(type -> {
            assertThat(type.title().ja()).isNotBlank();
            assertThat(type.title().en()).isNotBlank();
            assertThat(type.description().ja()).isNotBlank();
            assertThat(type.description().en()).isNotBlank();
            assertThat(type.resolution()).isNotNull();
            assertThat(type.resolution().ja()).isNotBlank();
            assertThat(type.resolution().en()).isNotBlank();
        });
    }

    @Test
    @DisplayName("the descriptions reveal neither a path nor an internal detail")
    void noInternalDetails() {
        assertThat(AccessProblemTypes.all()).allSatisfy(type -> {
            assertThat(type.description().ja()).doesNotContain("/api/");
            assertThat(type.description().en()).doesNotContain("/api/");
            assertThat(type.title().ja()).doesNotContain("/api/");
        });
    }

    @Test
    @DisplayName("the slugs follow the code of each problem type")
    void slugs() {
        assertThat(AccessProblemTypes.ACCESS_DENIED.slug()).isEqualTo("access-denied");
        assertThat(AccessProblemTypes.REQUEST_REJECTED.slug()).isEqualTo("request-rejected");
    }

    @Test
    @DisplayName("the codes do not clash with the ones of U1 and U2")
    void noClashWithOtherUnits() {
        assertThat(AccessProblemTypes.all())
                .extracting(ProblemType::code)
                .doesNotContainAnyElementsOf(
                        CommonProblemTypes.all().stream().map(ProblemType::code).toList())
                .doesNotContainAnyElementsOf(
                        AuthProblemTypes.all().stream().map(ProblemType::code).toList());
    }
}
