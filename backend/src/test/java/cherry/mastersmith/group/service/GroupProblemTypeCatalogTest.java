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
package cherry.mastersmith.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.error.service.CommonProblemTypeCatalog;
import cherry.mastersmith.common.error.service.ProblemTypeRegistry;
import cherry.mastersmith.group.domain.GroupProblemTypes;
import cherry.mastersmith.user.domain.UserProblemTypes;
import cherry.mastersmith.user.service.UserProblemTypeCatalog;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** GroupManagement の問題の種類の起動時の一覧（BR10.1・BR10.2）の単体テスト。 */
class GroupProblemTypeCatalogTest {

    @Test
    @DisplayName("the catalog returns every GroupManagement problem type and not USER_NOT_FOUND")
    void returnsAll() {
        assertThat(new GroupProblemTypeCatalog().problemTypes())
                .containsExactlyElementsOf(GroupProblemTypes.all())
                .extracting(ProblemType::code)
                .doesNotContain("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("the registry accepts it together with the common and UserAccount catalogs without duplicates")
    void registryAcceptsIt() {
        ProblemTypeRegistry registry = new ProblemTypeRegistry(
                List.of(new CommonProblemTypeCatalog(), new UserProblemTypeCatalog(), new GroupProblemTypeCatalog()));

        assertThat(registry.findByCode("GROUP_BUSY")).contains(GroupProblemTypes.GROUP_BUSY);
        assertThat(registry.findByCode("GROUP_NOT_FOUND")).contains(GroupProblemTypes.GROUP_NOT_FOUND);
        assertThat(registry.findByCode("USER_NOT_FOUND")).contains(UserProblemTypes.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("registering the catalog twice is refused by the duplicate check")
    void duplicateIsRefused() {
        assertThatThrownBy(() ->
                        new ProblemTypeRegistry(List.of(new GroupProblemTypeCatalog(), new GroupProblemTypeCatalog())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GROUP_NOT_FOUND");
    }
}
