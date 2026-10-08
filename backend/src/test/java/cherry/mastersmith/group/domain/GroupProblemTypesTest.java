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
package cherry.mastersmith.group.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.user.domain.UserProblemTypes;
import java.util.Arrays;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** GroupManagement の問題の種類と拒否の理由（BR10.1・BR10.2・BR8.2）の単体テスト。 */
class GroupProblemTypesTest {

    @Test
    @DisplayName("GROUP_NOT_FOUND is fixed to 404 and the four other codes to 409")
    void codeAndStatus() {
        assertThat(GroupProblemTypes.all())
                .extracting(ProblemType::code, ProblemType::status)
                .containsExactly(
                        Tuple.tuple("GROUP_NOT_FOUND", 404),
                        Tuple.tuple("GROUP_NAME_DUPLICATE", 409),
                        Tuple.tuple("GROUP_IN_USE", 409),
                        Tuple.tuple("GROUP_NO_CHANGE", 409),
                        Tuple.tuple("GROUP_BUSY", 409));
    }

    @Test
    @DisplayName("each rejection has exactly one problem type and USER_NOT_FOUND is the UserAccount definition")
    void rejectionToProblemType() {
        assertThat(GroupProblemTypes.of(GroupRejection.GROUP_NOT_FOUND)).isSameAs(GroupProblemTypes.GROUP_NOT_FOUND);
        assertThat(GroupProblemTypes.of(GroupRejection.USER_NOT_FOUND)).isSameAs(UserProblemTypes.USER_NOT_FOUND);
        assertThat(GroupProblemTypes.of(GroupRejection.NAME_DUPLICATE))
                .isSameAs(GroupProblemTypes.GROUP_NAME_DUPLICATE);
        assertThat(GroupProblemTypes.of(GroupRejection.IN_USE)).isSameAs(GroupProblemTypes.GROUP_IN_USE);
        assertThat(GroupProblemTypes.of(GroupRejection.NO_CHANGE)).isSameAs(GroupProblemTypes.GROUP_NO_CHANGE);
        assertThat(Arrays.stream(GroupRejection.values())
                        .map(GroupProblemTypes::of)
                        .distinct())
                .hasSize(GroupRejection.values().length)
                .doesNotContain(GroupProblemTypes.GROUP_BUSY);
        assertThat(GroupProblemTypes.all()).doesNotContain(UserProblemTypes.USER_NOT_FOUND);
        assertThatThrownBy(() -> GroupProblemTypes.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("each rejection is recorded with exactly one audit failure reason")
    void rejectionToAuditFailure() {
        assertThat(GroupRejection.GROUP_NOT_FOUND.auditFailure()).isEqualTo(GroupAuditFailure.GROUP_NOT_FOUND);
        assertThat(GroupRejection.USER_NOT_FOUND.auditFailure()).isEqualTo(GroupAuditFailure.USER_NOT_FOUND);
        assertThat(GroupRejection.NAME_DUPLICATE.auditFailure()).isEqualTo(GroupAuditFailure.GROUP_NAME_DUPLICATE);
        assertThat(GroupRejection.IN_USE.auditFailure()).isEqualTo(GroupAuditFailure.GROUP_IN_USE);
        assertThat(GroupRejection.NO_CHANGE.auditFailure()).isEqualTo(GroupAuditFailure.NO_CHANGE);
        assertThat(Arrays.stream(GroupRejection.values()).map(GroupRejection::auditFailure))
                .containsExactlyInAnyOrder(GroupAuditFailure.values());
    }

    @Test
    @DisplayName("every text exists in Japanese and English and has no placeholder for names or ids")
    void bilingualWithoutValues() {
        for (ProblemType type : GroupProblemTypes.all()) {
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
