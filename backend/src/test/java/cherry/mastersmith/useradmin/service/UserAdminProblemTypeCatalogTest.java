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
package cherry.mastersmith.useradmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.error.service.CommonProblemTypeCatalog;
import cherry.mastersmith.common.error.service.ProblemTypeRegistry;
import cherry.mastersmith.user.domain.UserProblemTypes;
import cherry.mastersmith.user.service.UserProblemTypeCatalog;
import cherry.mastersmith.useradmin.domain.UserAdminProblemTypes;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** UserAdministration の問題の種類の起動時の一覧の単体テスト。 */
class UserAdminProblemTypeCatalogTest {

    @Test
    @DisplayName("the catalog returns every UserAdministration problem type")
    void returnsAll() {
        assertThat(new UserAdminProblemTypeCatalog().problemTypes())
                .containsExactlyElementsOf(UserAdminProblemTypes.all());
    }

    @Test
    @DisplayName("USER_NOT_FOUND is collected by the UserAccount catalog, not by this one")
    void userNotFoundIsNotCollectedHere() {
        assertThat(new UserAdminProblemTypeCatalog().problemTypes())
                .extracting(ProblemType::code)
                .doesNotContain("USER_NOT_FOUND")
                .containsExactly(
                        "USER_ADMIN_SELF_OPERATION",
                        "USER_ADMIN_TARGET_SUSPENDED",
                        "USER_ADMIN_NO_CHANGE",
                        "USER_ADMIN_LAST_ADMIN",
                        "USER_ADMIN_BUSY");
        assertThat(new UserAdminProblemTypeCatalog().problemTypes()).doesNotContain(UserProblemTypes.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("the registry accepts it together with the UserAccount catalog without a duplicate USER_NOT_FOUND")
    void registryAcceptsItWithUserAccount() {
        ProblemTypeRegistry registry = new ProblemTypeRegistry(List.of(
                new CommonProblemTypeCatalog(), new UserProblemTypeCatalog(), new UserAdminProblemTypeCatalog()));

        assertThat(registry.findByCode("USER_NOT_FOUND")).contains(UserProblemTypes.USER_NOT_FOUND);
        assertThat(registry.findByCode("USER_ADMIN_BUSY")).contains(UserAdminProblemTypes.BUSY);
    }
}
