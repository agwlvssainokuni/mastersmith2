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
package cherry.mastersmith.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.error.domain.ProblemType;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** UserAccount の問題の種類（BR8.2、NFR8.1）の単体テスト。 */
class UserProblemTypesTest {

    @Test
    @DisplayName("the current-password mismatch is fixed to status 400 and USER_NOT_FOUND to 404")
    void codeAndStatus() {
        assertThat(UserProblemTypes.all())
                .extracting(ProblemType::code, ProblemType::status)
                .containsExactly(Tuple.tuple("PASSWORD_CURRENT_MISMATCH", 400), Tuple.tuple("USER_NOT_FOUND", 404));
    }

    @Test
    @DisplayName("USER_NOT_FOUND keeps the code and the texts it had in useradmin and shows no user values")
    void userNotFoundKeepsItsTexts() {
        ProblemType type = UserProblemTypes.USER_NOT_FOUND;

        assertThat(type.title().ja()).isEqualTo("利用者が見つかりません");
        assertThat(type.title().en()).isEqualTo("User not found");
        assertThat(type.description().ja()).isEqualTo("指定した利用者はいません。");
        assertThat(type.description().en()).isEqualTo("The specified user does not exist.");
        assertThat(type.resolution()).isNotNull();
        assertThat(type.resolution().ja()).isEqualTo("利用者の一覧を読み直してください。");
        assertThat(type.resolution().en()).isEqualTo("Reload the user list.");
        assertThat(type.slug()).isEqualTo("user-not-found");
    }

    @Test
    @DisplayName("the problem type has Japanese and English texts including a resolution")
    void bilingual() {
        ProblemType type = UserProblemTypes.PASSWORD_CURRENT_MISMATCH;

        assertThat(type.title().ja()).isNotBlank();
        assertThat(type.title().en()).isNotBlank();
        assertThat(type.description().ja()).isNotBlank();
        assertThat(type.description().en()).isNotBlank();
        assertThat(type.resolution()).isNotNull();
        assertThat(type.slug()).isEqualTo("password-current-mismatch");
    }
}
