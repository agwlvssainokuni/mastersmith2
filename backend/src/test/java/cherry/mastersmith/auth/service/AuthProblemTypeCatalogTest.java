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
package cherry.mastersmith.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.domain.AuthProblemTypes;
import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.error.service.CommonProblemTypeCatalog;
import cherry.mastersmith.common.error.service.ProblemTypeRegistry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthProblemTypeCatalogTest {

    @Test
    @DisplayName("the catalog returns the four U2 problem types")
    void returnsAll() {
        assertThat(new AuthProblemTypeCatalog().problemTypes()).containsExactlyElementsOf(AuthProblemTypes.all());
    }

    @Test
    @DisplayName("the U2 codes do not clash with the U1 codes in the registry")
    void noClash() {
        ProblemTypeRegistry registry =
                new ProblemTypeRegistry(List.of(new CommonProblemTypeCatalog(), new AuthProblemTypeCatalog()));

        assertThat(registry.all()).hasSize(CommonProblemTypes.all().size() + 4);
        assertThat(registry.findBySlug("origin-not-allowed")).contains(AuthProblemTypes.ORIGIN_NOT_ALLOWED);
    }
}
