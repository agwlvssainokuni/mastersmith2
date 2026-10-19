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

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 業務データの読み取りの口の結果（BR8.1、NFR 設計のレビュー R-02）の単体テスト。 */
class TargetReadOutcomeTest {

    /** 値を持つ型の偽物（文字列にすると値が出る）。 */
    private record Secret(String value) {}

    @Test
    @DisplayName("done shows the simple name of the value type only, never the value")
    void doneHidesTheValue() {
        TargetReadOutcome<Secret> done = new TargetReadOutcome.Done<>(new Secret("ペン 100 円"));

        assertThat(done.toString()).isEqualTo("Done[valueType=Secret]").doesNotContain("ペン");
    }

    @Test
    @DisplayName("done with a null value says null")
    void doneWithNull() {
        assertThat(new TargetReadOutcome.Done<>(null).toString()).isEqualTo("Done[valueType=null]");
    }

    @Test
    @DisplayName("done keeps the value for the caller")
    void doneKeepsTheValue() {
        Secret secret = new Secret("x");

        assertThat(new TargetReadOutcome.Done<>(secret).value()).isSameAs(secret);
    }

    @Test
    @DisplayName("unavailable has no message or cause, only the SQLState, the vendor code and the class name")
    void unavailableHasNoMessage() {
        List<String> names = Arrays.stream(TargetReadOutcome.Unavailable.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(names).containsExactly("sqlState", "vendorCode", "exceptionClass");
        assertThat(new TargetReadOutcome.Unavailable<String>("08001", 0, "java.sql.SQLException").toString())
                .contains("08001")
                .contains("java.sql.SQLException");
    }

    @Test
    @DisplayName("not configured carries nothing")
    void notConfiguredCarriesNothing() {
        assertThat(TargetReadOutcome.NotConfigured.class.getRecordComponents()).isEmpty();
        assertThat(new TargetReadOutcome.NotConfigured<String>()).isEqualTo(new TargetReadOutcome.NotConfigured<>());
    }

    @Test
    @DisplayName("the unexpected exception has neither a message nor a cause")
    void unexpectedExceptionHasNoMessage() {
        TargetReadUnexpectedException exception = new TargetReadUnexpectedException("NumberFormatException");

        assertThat(exception.getMessage()).isNull();
        assertThat(exception.getCause()).isNull();
        assertThat(exception.exceptionClass()).isEqualTo("NumberFormatException");
        assertThat(exception.toString()).doesNotContain(":");
    }
}
