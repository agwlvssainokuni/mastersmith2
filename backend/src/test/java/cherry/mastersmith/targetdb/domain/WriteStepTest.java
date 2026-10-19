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
package cherry.mastersmith.targetdb.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 書き込みの口に渡した処理の答え（Intent 261016-business-data-views の U2、BR9.3）の単体テスト。 */
class WriteStepTest {

    @Test
    @DisplayName("each answer shows only its kind, never the result")
    void stringFormShowsOnlyTheKind() {
        assertThat(new WriteStep.Commit<>("値-秘密").toString()).isEqualTo("Commit");
        assertThat(new WriteStep.Rollback<>("値-秘密").toString()).isEqualTo("Rollback");
        assertThat(new WriteStep.RollbackToSavepointThenInspect<String>(session -> "値-秘密", failure -> "失敗").toString())
                .isEqualTo("RollbackToSavepointThenInspect");
    }

    @Test
    @DisplayName("the answers keep their results and functions")
    void keepsTheResults() {
        WriteStep.RollbackToSavepointThenInspect<String> inspect = new WriteStep.RollbackToSavepointThenInspect<>(
                session -> "見つけた", failure -> failure.stage().name());

        assertThat(new WriteStep.Commit<>(1).result()).isEqualTo(1);
        assertThat(new WriteStep.Rollback<>(2).result()).isEqualTo(2);
        assertThat(inspect.onFailure().apply(new TargetWriteFailure(TargetWriteStage.INSPECT, null, null, "X", false)))
                .isEqualTo("INSPECT");
    }

    @Test
    @DisplayName("the inspection and the failure answer are required")
    void inspectionFunctionsAreRequired() {
        assertThatThrownBy(() -> new WriteStep.RollbackToSavepointThenInspect<String>(null, failure -> "x"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WriteStep.RollbackToSavepointThenInspect<String>(session -> "x", null))
                .isInstanceOf(NullPointerException.class);
    }
}
