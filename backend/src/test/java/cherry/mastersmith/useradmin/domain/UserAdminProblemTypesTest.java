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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import java.util.Arrays;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** UserAdministration の問題の種類（BR2.3・BR7.5・NFR8.1）の単体テスト。 */
class UserAdminProblemTypesTest {

    @Test
    @DisplayName("USER_NOT_FOUND is fixed to status 404 and the five operation codes to 409")
    void codeAndStatus() {
        assertThat(UserAdminProblemTypes.all())
                .extracting(ProblemType::code, ProblemType::status)
                .containsExactly(
                        Tuple.tuple("USER_NOT_FOUND", 404),
                        Tuple.tuple("USER_ADMIN_SELF_OPERATION", 409),
                        Tuple.tuple("USER_ADMIN_TARGET_SUSPENDED", 409),
                        Tuple.tuple("USER_ADMIN_NO_CHANGE", 409),
                        Tuple.tuple("USER_ADMIN_LAST_ADMIN", 409),
                        Tuple.tuple("USER_ADMIN_BUSY", 409));
    }

    @Test
    @DisplayName("each rejection reason has exactly one problem type")
    void reasonToProblemType() {
        assertThat(UserAdminProblemTypes.of(RejectionReason.USER_NOT_FOUND))
                .isSameAs(UserAdminProblemTypes.USER_NOT_FOUND);
        assertThat(UserAdminProblemTypes.of(RejectionReason.SELF_OPERATION))
                .isSameAs(UserAdminProblemTypes.SELF_OPERATION);
        assertThat(UserAdminProblemTypes.of(RejectionReason.TARGET_SUSPENDED))
                .isSameAs(UserAdminProblemTypes.TARGET_SUSPENDED);
        assertThat(UserAdminProblemTypes.of(RejectionReason.NO_CHANGE)).isSameAs(UserAdminProblemTypes.NO_CHANGE);
        assertThat(UserAdminProblemTypes.of(RejectionReason.LAST_ACTIVE_ADMIN))
                .isSameAs(UserAdminProblemTypes.LAST_ADMIN);
        assertThat(Arrays.stream(RejectionReason.values())
                        .map(UserAdminProblemTypes::of)
                        .distinct())
                .hasSize(RejectionReason.values().length)
                .doesNotContain(UserAdminProblemTypes.BUSY);
        assertThatThrownBy(() -> UserAdminProblemTypes.of(null)).isInstanceOf(NullPointerException.class);
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
