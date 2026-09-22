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

import java.time.Duration;
import java.time.Instant;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LockPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

    private static final Duration LOCK = Duration.ofMinutes(30);

    @Property
    @Label("failures below threshold minus one never lock and count up by one")
    void belowThreshold(
            @ForAll @IntRange(min = 1, max = 20) int threshold, @ForAll @IntRange(min = 0, max = 20) int failures) {
        if (failures + 1 >= threshold) {
            return;
        }
        LockDecision decision = LockPolicy.decide(new LockState(failures, null), false, NOW, threshold, LOCK);

        assertThat(decision.outcome()).isEqualTo(LoginOutcome.PASSWORD_MISMATCH);
        assertThat(decision.next()).isEqualTo(new LockState(failures + 1, null));
        assertThat(decision.lockedNow()).isFalse();
    }

    @Property
    @Label("the failure that reaches the threshold locks until now plus the lock duration")
    void reachingThresholdLocks(@ForAll @IntRange(min = 1, max = 20) int threshold) {
        LockDecision decision = LockPolicy.decide(new LockState(threshold - 1, null), false, NOW, threshold, LOCK);

        assertThat(decision.next()).isEqualTo(new LockState(threshold, NOW.plus(LOCK)));
        assertThat(decision.lockedNow()).isTrue();
    }

    @Property
    @Label("while locked even a matching password is rejected and the state does not change")
    void lockedRejects(
            @ForAll boolean matched,
            @ForAll @IntRange(min = 1, max = 1799) int secondsBeforeUnlock,
            @ForAll @IntRange(min = 0, max = 20) int failures) {
        LockState state = new LockState(failures, NOW.plusSeconds(secondsBeforeUnlock));

        LockDecision decision = LockPolicy.decide(state, matched, NOW, 5, LOCK);

        assertThat(decision.outcome()).isEqualTo(LoginOutcome.ACCOUNT_LOCKED);
        assertThat(decision.next()).isSameAs(state);
    }

    @Property
    @Label("at or after the unlock time the attempt is judged from zero failures")
    void unlockResets(@ForAll @IntRange(min = 0, max = 100_000) int secondsAfterUnlock, @ForAll boolean matched) {
        LockState state = new LockState(5, NOW.minusSeconds(secondsAfterUnlock));

        LockDecision decision = LockPolicy.decide(state, matched, NOW, 5, LOCK);

        assertThat(decision.next()).isEqualTo(matched ? LockState.CLEAR : new LockState(1, null));
    }

    @Property
    @Label("a successful attempt that is not locked resets the failures to zero")
    void successResets(@ForAll @IntRange(min = 0, max = 20) int failures) {
        LockDecision decision = LockPolicy.decide(new LockState(failures, null), true, NOW, 5, LOCK);

        assertThat(decision.outcome()).isEqualTo(LoginOutcome.SUCCEEDED);
        assertThat(decision.next()).isEqualTo(LockState.CLEAR);
    }

    @Test
    @DisplayName("examples: the fourth failure does not lock and the fifth locks for thirty minutes")
    void thresholdExamples() {
        assertThat(LockPolicy.decide(new LockState(3, null), false, NOW, 5, LOCK)
                        .next())
                .isEqualTo(new LockState(4, null));
        assertThat(LockPolicy.decide(new LockState(4, null), false, NOW, 5, LOCK)
                        .next())
                .isEqualTo(new LockState(5, NOW.plus(LOCK)));
    }

    @Test
    @DisplayName(
            "examples: exactly thirty minutes after the lock a correct password succeeds and a wrong one counts one")
    void unlockExamples() {
        LockState locked = new LockState(5, NOW.plus(LOCK));
        Instant unlockTime = NOW.plus(LOCK);

        assertThat(LockPolicy.decide(locked, false, unlockTime.minusSeconds(1), 5, LOCK)
                        .outcome())
                .isEqualTo(LoginOutcome.ACCOUNT_LOCKED);
        assertThat(LockPolicy.decide(locked, true, unlockTime, 5, LOCK).outcome())
                .isEqualTo(LoginOutcome.SUCCEEDED);
        assertThat(LockPolicy.decide(locked, false, unlockTime, 5, LOCK).next()).isEqualTo(new LockState(1, null));
    }

    @Test
    @DisplayName("invalid inputs are rejected")
    void rejectsInvalid() {
        assertThatThrownBy(() -> LockPolicy.decide(LockState.CLEAR, false, NOW, 0, LOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LockState(-1, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
