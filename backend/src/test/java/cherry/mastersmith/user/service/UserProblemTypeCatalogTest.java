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
package cherry.mastersmith.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.auth.service.AuthProblemTypeCatalog;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.error.service.CommonProblemTypeCatalog;
import cherry.mastersmith.common.error.service.ProblemTypeRegistry;
import cherry.mastersmith.user.domain.UserProblemTypes;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** UserAccount の問題の種類の登録（BR8.2）の単体テスト。 */
class UserProblemTypeCatalogTest {

    @Test
    @DisplayName("the catalog offers the current-password mismatch fixed to 400")
    void catalog() {
        assertThat(new UserProblemTypeCatalog().problemTypes())
                .extracting(ProblemType::code, ProblemType::status)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("PASSWORD_CURRENT_MISMATCH", 400));
    }

    @Test
    @DisplayName("the registry collects it together with the common and authentication types without duplicates")
    void registryAcceptsIt() {
        ProblemTypeRegistry registry = new ProblemTypeRegistry(
                List.of(new CommonProblemTypeCatalog(), new AuthProblemTypeCatalog(), new UserProblemTypeCatalog()));

        assertThat(registry.findByCode("PASSWORD_CURRENT_MISMATCH"))
                .contains(UserProblemTypes.PASSWORD_CURRENT_MISMATCH);
        assertThat(registry.findByCode("AUTHENTICATION_REQUIRED")).isPresent();
        assertThat(registry.findByCode("VALIDATION_FAILED")).isPresent();
    }

    @Test
    @DisplayName("registering the catalog twice is refused by the duplicate check")
    void duplicateIsRefused() {
        assertThatThrownBy(() ->
                        new ProblemTypeRegistry(List.of(new UserProblemTypeCatalog(), new UserProblemTypeCatalog())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PASSWORD_CURRENT_MISMATCH");
    }
}
