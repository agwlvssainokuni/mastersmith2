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
package cherry.mastersmith.role.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import java.util.Arrays;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RoleManagement の問題の種類と拒否の理由（BR13.1・BR13.2・BR11.2、計画の 4.2）の単体テスト。 */
class RoleProblemTypesTest {

    @Test
    @DisplayName("ROLE_NOT_FOUND is fixed to 404 and the six other codes of B4 to 409")
    void codeAndStatus() {
        assertThat(RoleProblemTypes.all())
                .extracting(ProblemType::code, ProblemType::status)
                .containsExactly(
                        Tuple.tuple("ROLE_NOT_FOUND", 404),
                        Tuple.tuple("ROLE_NAME_DUPLICATE", 409),
                        Tuple.tuple("ROLE_IN_USE", 409),
                        Tuple.tuple("ROLE_NO_CHANGE", 409),
                        Tuple.tuple("ROLE_BUSY", 409),
                        Tuple.tuple("PERMISSION_TARGET_NOT_IN_DSL", 409),
                        Tuple.tuple("DSL_NOT_APPLIED", 409));
    }

    @Test
    @DisplayName("each rejection has exactly one problem type, and ROLE_BUSY is not a rejection")
    void rejectionToProblemType() {
        assertThat(RoleProblemTypes.of(RoleRejection.ROLE_NOT_FOUND)).isSameAs(RoleProblemTypes.ROLE_NOT_FOUND);
        assertThat(RoleProblemTypes.of(RoleRejection.NAME_DUPLICATE)).isSameAs(RoleProblemTypes.ROLE_NAME_DUPLICATE);
        assertThat(RoleProblemTypes.of(RoleRejection.IN_USE)).isSameAs(RoleProblemTypes.ROLE_IN_USE);
        assertThat(RoleProblemTypes.of(RoleRejection.NO_CHANGE)).isSameAs(RoleProblemTypes.ROLE_NO_CHANGE);
        assertThat(RoleProblemTypes.of(RoleRejection.TARGET_NOT_IN_DSL))
                .isSameAs(RoleProblemTypes.PERMISSION_TARGET_NOT_IN_DSL);
        assertThat(RoleProblemTypes.of(RoleRejection.DSL_NOT_APPLIED)).isSameAs(RoleProblemTypes.DSL_NOT_APPLIED);
        assertThat(Arrays.stream(RoleRejection.values())
                        .map(RoleProblemTypes::of)
                        .distinct())
                .hasSize(RoleRejection.values().length)
                .doesNotContain(RoleProblemTypes.ROLE_BUSY);
        assertThatThrownBy(() -> RoleProblemTypes.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("each rejection is recorded with exactly one audit failure reason")
    void rejectionToAuditFailure() {
        assertThat(RoleRejection.ROLE_NOT_FOUND.auditFailure()).isEqualTo(RoleAuditFailure.ROLE_NOT_FOUND);
        assertThat(RoleRejection.NAME_DUPLICATE.auditFailure()).isEqualTo(RoleAuditFailure.ROLE_NAME_DUPLICATE);
        assertThat(RoleRejection.IN_USE.auditFailure()).isEqualTo(RoleAuditFailure.ROLE_IN_USE);
        assertThat(RoleRejection.NO_CHANGE.auditFailure()).isEqualTo(RoleAuditFailure.NO_CHANGE);
        assertThat(RoleRejection.TARGET_NOT_IN_DSL.auditFailure())
                .isEqualTo(RoleAuditFailure.PERMISSION_TARGET_NOT_IN_DSL);
        assertThat(RoleRejection.DSL_NOT_APPLIED.auditFailure()).isEqualTo(RoleAuditFailure.DSL_NOT_APPLIED);
        assertThat(Arrays.stream(RoleRejection.values()).map(RoleRejection::auditFailure))
                .containsExactlyInAnyOrder(RoleAuditFailure.values());
    }

    @Test
    @DisplayName("every text exists in Japanese and English and has no placeholder for names or ids")
    void bilingualWithoutValues() {
        for (ProblemType type : RoleProblemTypes.all()) {
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
