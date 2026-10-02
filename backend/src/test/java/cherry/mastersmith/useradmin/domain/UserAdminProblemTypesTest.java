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
package cherry.mastersmith.useradmin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** UserAdministration の問題の種類（BR2.3・BR7.5・NFR8.1）の単体テスト。 */
class UserAdminProblemTypesTest {

    @Test
    @DisplayName("USER_NOT_FOUND is fixed to status 404")
    void codeAndStatus() {
        assertThat(UserAdminProblemTypes.all())
                .extracting(ProblemType::code, ProblemType::status)
                .containsExactly(Tuple.tuple("USER_NOT_FOUND", 404));
    }

    @Test
    @DisplayName("every text exists in Japanese and English and has no placeholder for user values")
    void bilingualWithoutUserValues() {
        for (ProblemType type : UserAdminProblemTypes.all()) {
            assertThat(type.resolution()).isNotNull();
            Stream.of(type.title(), type.description(), type.resolution())
                    .flatMap((LocalizedText text) -> Stream.of(text.ja(), text.en()))
                    .forEach(text -> assertThat(text)
                            .isNotBlank()
                            .doesNotContain("{")
                            .doesNotContain("%s")
                            .doesNotContain("@"));
        }
    }
}
