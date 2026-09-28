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
package cherry.mastersmith.invitation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** 有効の判定と拒否の理由の単体テスト（BR3.3・BR7.6、NFR1.3、性質ベースのテストを含む）。 */
class InvitationValidityTest {

    private static final Instant EXPIRES = Instant.parse("2026-09-23T00:00:00Z");

    @Test
    @DisplayName("a pending invitation is valid one nanosecond before the expiry")
    void validJustBefore() {
        Instant now = EXPIRES.minusNanos(1);

        assertThat(InvitationValidity.isValid(InvitationState.PENDING, EXPIRES, now))
                .isTrue();
        assertThat(InvitationValidity.isExpired(InvitationState.PENDING, EXPIRES, now))
                .isFalse();
        assertThat(InvitationValidity.rejectionOf(InvitationState.PENDING, EXPIRES, now))
                .isEmpty();
    }

    @Test
    @DisplayName("a pending invitation is invalid and expired exactly at and after the expiry")
    void invalidAtAndAfter() {
        for (Instant now : new Instant[] {EXPIRES, EXPIRES.plusNanos(1)}) {
            assertThat(InvitationValidity.isValid(InvitationState.PENDING, EXPIRES, now))
                    .isFalse();
            assertThat(InvitationValidity.isExpired(InvitationState.PENDING, EXPIRES, now))
                    .isTrue();
            assertThat(InvitationValidity.rejectionOf(InvitationState.PENDING, EXPIRES, now))
                    .contains(LinkRejection.INVITATION_EXPIRED);
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = InvitationState.class,
            names = {"COMPLETED", "CANCELLED", "REPLACED"})
    @DisplayName("an ended invitation is never valid nor expired even before the expiry")
    void endedIsNeverValid(InvitationState state) {
        Instant now = EXPIRES.minus(Duration.ofHours(1));

        assertThat(InvitationValidity.isValid(state, EXPIRES, now)).isFalse();
        assertThat(InvitationValidity.isExpired(state, EXPIRES, now)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"COMPLETED, INVITATION_ALREADY_USED", "CANCELLED, INVITATION_CANCELLED", "REPLACED, INVITATION_EXPIRED"
    })
    @DisplayName("the rejection reason follows the state (BR7.6)")
    void rejectionTable(InvitationState state, LinkRejection reason) {
        assertThat(InvitationValidity.rejectionOf(state, EXPIRES, EXPIRES.minusSeconds(10)))
                .contains(reason);
        assertThat(InvitationValidity.rejectionOf(state, EXPIRES, EXPIRES.plusSeconds(10)))
                .contains(reason);
    }

    @Property(tries = 500)
    @Label("valid holds exactly when the state is PENDING and now is before the expiry")
    void validEquivalence(
            @ForAll InvitationState state, @ForAll @LongRange(min = -100_000, max = 100_000) long offset) {
        Instant now = EXPIRES.plusMillis(offset);
        boolean expected = state == InvitationState.PENDING && now.isBefore(EXPIRES);

        assertThat(InvitationValidity.isValid(state, EXPIRES, now)).isEqualTo(expected);
        assertThat(InvitationValidity.rejectionOf(state, EXPIRES, now).isEmpty())
                .isEqualTo(expected);
    }
}
