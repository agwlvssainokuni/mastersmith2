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
package cherry.mastersmith.targetdb.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import cherry.mastersmith.common.testsupport.LogEvents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

/** コンテナの実行環境の確かめの単体テスト（NFR12.3、team.md の Way of Working）。 */
class ContainerRuntimeCheckTest {

    @Test
    @DisplayName("with a container runtime the target database tests go ahead")
    void available() {
        assertThatCode(() -> ContainerRuntimeCheck.decide(true, false)).doesNotThrowAnyException();
        assertThatCode(() -> ContainerRuntimeCheck.decide(true, true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("without a container runtime on CI the tests fail instead of being skipped")
    void failsOnCi() {
        assertThatThrownBy(() -> ContainerRuntimeCheck.decide(false, true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("without a container runtime during development the tests are aborted with one warning")
    void abortsWithWarningLocally() {
        try (LogEvents events = LogEvents.capture(ContainerRuntimeCheck.class)) {
            assertThatThrownBy(() -> ContainerRuntimeCheck.decide(false, false))
                    .isInstanceOf(TestAbortedException.class)
                    .hasMessage(ContainerRuntimeCheck.SKIP_MESSAGE);

            assertThat(events.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("この状態では統合しない");
            });
        }
    }
}
