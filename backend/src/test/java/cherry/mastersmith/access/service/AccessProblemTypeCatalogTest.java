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
package cherry.mastersmith.access.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.access.domain.AccessProblemTypes;
import cherry.mastersmith.common.error.domain.ProblemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccessProblemTypeCatalogTest {

    private final AccessProblemTypeCatalog catalog = new AccessProblemTypeCatalog();

    @Test
    @DisplayName("the catalog offers the two problem types of U3 to the startup collection of U1")
    void offersBothProblemTypes() {
        assertThat(catalog.problemTypes())
                .extracting(ProblemType::code)
                .containsExactly("ACCESS_DENIED", "REQUEST_REJECTED");
    }

    @Test
    @DisplayName("the catalog returns the same definitions as the problem type holder")
    void sameAsHolder() {
        assertThat(catalog.problemTypes()).isEqualTo(AccessProblemTypes.all());
    }
}
