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
package cherry.mastersmith.role.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.error.service.CommonProblemTypeCatalog;
import cherry.mastersmith.common.error.service.ProblemTypeRegistry;
import cherry.mastersmith.group.service.GroupProblemTypeCatalog;
import cherry.mastersmith.role.domain.RoleProblemTypes;
import cherry.mastersmith.user.service.UserProblemTypeCatalog;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RoleManagement の問題の種類の起動時の一覧（BR13.1・BR13.2）の単体テスト。 */
class RoleProblemTypeCatalogTest {

    @Test
    @DisplayName("the catalog returns every RoleManagement problem type and no shared code")
    void returnsAll() {
        assertThat(new RoleProblemTypeCatalog().problemTypes())
                .containsExactlyElementsOf(RoleProblemTypes.all())
                .extracting(ProblemType::code)
                .doesNotContain("VALIDATION_FAILED", "USER_NOT_FOUND", "GROUP_NOT_FOUND", "GROUP_BUSY");
    }

    @Test
    @DisplayName("the registry accepts it together with the common, UserAccount and GroupManagement catalogs")
    void registryAcceptsIt() {
        ProblemTypeRegistry registry = new ProblemTypeRegistry(List.of(
                new CommonProblemTypeCatalog(),
                new UserProblemTypeCatalog(),
                new GroupProblemTypeCatalog(),
                new RoleProblemTypeCatalog()));

        assertThat(registry.findByCode("ROLE_BUSY")).contains(RoleProblemTypes.ROLE_BUSY);
        assertThat(registry.findByCode("DSL_NOT_APPLIED")).contains(RoleProblemTypes.DSL_NOT_APPLIED);
    }

    @Test
    @DisplayName("registering the catalog twice is refused by the duplicate check")
    void duplicateIsRefused() {
        assertThatThrownBy(() ->
                        new ProblemTypeRegistry(List.of(new RoleProblemTypeCatalog(), new RoleProblemTypeCatalog())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ROLE_NOT_FOUND");
    }
}
