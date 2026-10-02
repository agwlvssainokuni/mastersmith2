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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 一覧のロックの判定（BR1.7、R-05）の単体テスト。 */
class LockViewTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

    private static LoginAttemptState row(int failures, Instant lockedUntil) {
        return new LoginAttemptState(10L, failures, lockedUntil);
    }

    @Test
    @DisplayName("a user without a row is not locked and not resettable")
    void noRow() {
        assertThat(LockView.of(null, NOW)).isEqualTo(new LockView(false, null, false));
    }

    @Test
    @DisplayName("zero failures without an unlock time is not resettable")
    void clear() {
        assertThat(LockView.of(row(0, null), NOW)).isEqualTo(new LockView(false, null, false));
    }

    @Test
    @DisplayName("failures without an unlock time are resettable but not locked")
    void failuresOnly() {
        assertThat(LockView.of(row(3, null), NOW)).isEqualTo(new LockView(false, null, true));
    }

    @Test
    @DisplayName("before the unlock time the user is locked until that time")
    void locked() {
        Instant until = NOW.plusSeconds(1);

        assertThat(LockView.of(row(5, until), NOW)).isEqualTo(new LockView(true, until, true));
    }

    @Test
    @DisplayName("exactly at the unlock time the user is not locked but still resettable")
    void exactlyAtUnlock() {
        assertThat(LockView.of(row(5, NOW), NOW)).isEqualTo(new LockView(false, null, true));
    }

    @Test
    @DisplayName("after the unlock time the user is not locked but still resettable")
    void afterUnlock() {
        assertThat(LockView.of(row(5, NOW.minusSeconds(1)), NOW)).isEqualTo(new LockView(false, null, true));
    }

    @Test
    @DisplayName("zero failures with an unlock time is still resettable (R-05 guard)")
    void brokenRowIsResettable() {
        Instant until = NOW.plusSeconds(60);

        assertThat(LockView.of(row(0, until), NOW)).isEqualTo(new LockView(true, until, true));
        assertThat(LockView.of(row(0, NOW), NOW)).isEqualTo(new LockView(false, null, true));
    }

    @Test
    @DisplayName("inconsistent views cannot be made")
    void invariants() {
        assertThatThrownBy(() -> new LockView(true, null, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LockView(false, NOW, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LockView(true, NOW, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Property
    @Label("locked implies an unlock time and resettable, unlocked has no unlock time, resettable matches the rule")
    void properties(
            @ForAll @IntRange(min = 0, max = 50) int failures,
            @ForAll boolean hasUntil,
            @ForAll @IntRange(min = -100_000, max = 100_000) int untilOffsetSeconds) {
        Instant until = hasUntil ? NOW.plusSeconds(untilOffsetSeconds) : null;

        LockView view = LockView.of(row(failures, until), NOW);

        if (view.locked()) {
            assertThat(view.lockedUntil()).isEqualTo(until).isAfter(NOW);
            assertThat(view.resettable()).isTrue();
        } else {
            assertThat(view.lockedUntil()).isNull();
        }
        assertThat(view.resettable()).isEqualTo(failures >= 1 || until != null);
    }
}
