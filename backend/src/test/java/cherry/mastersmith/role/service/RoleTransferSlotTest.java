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
package cherry.mastersmith.role.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import cherry.mastersmith.common.testsupport.LogEvents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 確かめと適用の入口（{@code reliability-design.md} 2.6、計画の D-20、NFR 設計の読み直しの R-12）の単体テスト。 */
class RoleTransferSlotTest {

    @Test
    @DisplayName("only one holder at a time, and the slot can be taken again after it is released")
    void oneAtATime() {
        RoleTransferSlot slot = new RoleTransferSlot();

        assertThat(slot.tryAcquire()).isTrue();
        try (LogEvents logs = LogEvents.capture(RoleTransferSlot.class)) {
            assertThat(slot.tryAcquire()).isFalse();
        }
        slot.release();
        assertThat(slot.tryAcquire()).isTrue();
    }

    @Test
    @DisplayName("a refusal writes one WARN with only the lockKind and no exception class")
    void warnWithoutException() {
        RoleTransferSlot slot = new RoleTransferSlot();
        slot.tryAcquire();

        try (LogEvents logs = LogEvents.capture(RoleTransferSlot.class)) {
            slot.tryAcquire();

            assertThat(logs.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).isEqualTo(RoleTransferSlot.WARN_MESSAGE);
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getKeyValuePairs())
                        .singleElement()
                        .satisfies(pair ->
                                assertThat(pair.key + "=" + pair.value).isEqualTo("lockKind=ROLE_TRANSFER_SLOT"));
            });
        }
    }

    @Test
    @DisplayName("a successful take writes no log")
    void noLogOnSuccess() {
        try (LogEvents logs = LogEvents.capture(RoleTransferSlot.class)) {
            new RoleTransferSlot().tryAcquire();

            assertThat(logs.list()).isEmpty();
        }
    }
}
