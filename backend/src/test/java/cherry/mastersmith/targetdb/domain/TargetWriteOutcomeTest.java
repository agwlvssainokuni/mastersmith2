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

/** 業務データの書き込みの口の結果（Intent 261016-business-data-views の U2、BR9.3）の単体テスト。 */
class TargetWriteOutcomeTest {

    private record Secret(String value) {}

    @Test
    @DisplayName("Done shows only the simple name of the value type, never the value")
    void doneHidesTheValue() {
        TargetWriteOutcome<Secret> done = new TargetWriteOutcome.Done<>(new Secret("行の値-秘密"));

        assertThat(done.toString()).isEqualTo("Done[valueType=Secret]").doesNotContain("秘密");
        assertThat(new TargetWriteOutcome.Done<>(null).toString()).isEqualTo("Done[valueType=null]");
    }

    @Test
    @DisplayName("a failure keeps the stage, the codes and the class name and requires the stage and class")
    void failureKeepsCodesOnly() {
        TargetWriteFailure failure =
                new TargetWriteFailure(TargetWriteStage.COMMIT, "08006", 0, "java.sql.SQLException", true);

        assertThat(new TargetWriteOutcome.Failed<>(failure).failure()).isEqualTo(failure);
        assertThat(failure.toString())
                .isEqualTo("TargetWriteFailure[stage=COMMIT, sqlState=08006, vendorCode=0,"
                        + " exceptionClass=java.sql.SQLException, interrupted=true]");
        assertThatThrownBy(() -> new TargetWriteFailure(null, null, null, "x", false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetWriteFailure(TargetWriteStage.WORK, null, null, null, false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetWriteOutcome.Failed<>(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the unexpected exception carries neither a message nor a cause, only the class name")
    void unexpectedExceptionHasNoMessage() {
        TargetWriteUnexpectedException e = new TargetWriteUnexpectedException("NumberFormatException");

        assertThat(e.getMessage()).isNull();
        assertThat(e.getCause()).isNull();
        assertThat(e.getStackTrace()).isNotEmpty();
        assertThat(e.exceptionClass()).isEqualTo("NumberFormatException");
    }
}
