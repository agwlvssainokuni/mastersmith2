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
package cherry.mastersmith.internaldb.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import cherry.mastersmith.common.testsupport.LogEvents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

/** 内部DB の PostgreSQL のテストのコンテナの実行環境の確かめの単体テスト（BR6.4・T9）。 */
class InternalDbPostgresRuntimeCheckTest {

    @Test
    @DisplayName("with a container runtime the tests go ahead whether or not it is required")
    void available() {
        assertThatCode(() -> InternalDbPostgresRuntimeCheck.decide(true, false)).doesNotThrowAnyException();
        assertThatCode(() -> InternalDbPostgresRuntimeCheck.decide(true, true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("without a container runtime where it is required the tests fail instead of being skipped")
    void failsWhenRequired() {
        assertThatThrownBy(() -> InternalDbPostgresRuntimeCheck.decide(false, true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("CI or the property requires a container runtime, otherwise it is optional")
    void requiresContainerRuntime() {
        assertThat(InternalDbPostgresRuntimeCheck.requiresContainerRuntime("true", null))
                .isTrue();
        assertThat(InternalDbPostgresRuntimeCheck.requiresContainerRuntime(null, "true"))
                .isTrue();
        assertThat(InternalDbPostgresRuntimeCheck.requiresContainerRuntime("TRUE", "false"))
                .isTrue();
        assertThat(InternalDbPostgresRuntimeCheck.requiresContainerRuntime(null, null))
                .isFalse();
        assertThat(InternalDbPostgresRuntimeCheck.requiresContainerRuntime("false", ""))
                .isFalse();
    }

    @Test
    @DisplayName("without a container runtime during development the tests are aborted with one warning")
    void abortsWithWarningLocally() {
        try (LogEvents events = LogEvents.capture(InternalDbPostgresRuntimeCheck.class)) {
            assertThatThrownBy(() -> InternalDbPostgresRuntimeCheck.decide(false, false))
                    .isInstanceOf(TestAbortedException.class)
                    .hasMessage(InternalDbPostgresRuntimeCheck.SKIP_MESSAGE);

            assertThat(events.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("この状態では統合しない");
            });
        }
    }
}
