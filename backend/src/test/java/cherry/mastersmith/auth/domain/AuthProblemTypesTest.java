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
package cherry.mastersmith.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.error.domain.ProblemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthProblemTypesTest {

    @Test
    @DisplayName("four problem types with their codes and status codes")
    void codesAndStatuses() {
        assertThat(AuthProblemTypes.all())
                .extracting(ProblemType::code, ProblemType::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("AUTHENTICATION_FAILED", 401),
                        org.assertj.core.groups.Tuple.tuple("AUTHENTICATION_REQUIRED", 401),
                        org.assertj.core.groups.Tuple.tuple("REFRESH_FAILED", 401),
                        org.assertj.core.groups.Tuple.tuple("ORIGIN_NOT_ALLOWED", 403));
    }

    @Test
    @DisplayName("every problem type has Japanese and English texts including a resolution")
    void bilingual() {
        assertThat(AuthProblemTypes.all()).allSatisfy(type -> {
            assertThat(type.title().ja()).isNotBlank();
            assertThat(type.title().en()).isNotBlank();
            assertThat(type.description().ja()).isNotBlank();
            assertThat(type.resolution()).isNotNull();
        });
    }

    @Test
    @DisplayName("the login failure description does not reveal the reason")
    void noReasonInFailure() {
        assertThat(AuthProblemTypes.AUTHENTICATION_FAILED.description().en())
                .doesNotContainIgnoringCase("locked")
                .doesNotContainIgnoringCase("not found");
    }
}
