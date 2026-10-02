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

import cherry.mastersmith.useradmin.domain.UserAdminProblemTypes;
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
}
