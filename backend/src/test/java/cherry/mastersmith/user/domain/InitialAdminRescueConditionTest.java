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
package cherry.mastersmith.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 初期管理者の救済の条件のつなぎ方（Intent 261004-safety-carryover の FR1.6a）と、救済の出来事の形（FR1.5）の単体テスト。
 *
 * <p>性質ベースのテスト（jqwik）を含む。失敗時の乱数の種は失敗の報告に出るため、{@code @Property(seed = "...")} に与えて再現する
 * （{@code junit-platform.properties}）。
 */
class InitialAdminRescueConditionTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-04T01:02:03Z");

    @Test
    @DisplayName("all three conditions are joined in the declared order")
    void allThree() {
        assertThat(InitialAdminRescueCondition.code(EnumSet.allOf(InitialAdminRescueCondition.class)))
                .isEqualTo("SUSPENDED+NO_ADMIN+PASSWORD");
    }

    @ParameterizedTest
    @EnumSource(InitialAdminRescueCondition.class)
    @DisplayName("a single condition is its own name")
    void single(InitialAdminRescueCondition condition) {
        assertThat(InitialAdminRescueCondition.code(Set.of(condition))).isEqualTo(condition.name());
    }

    @Test
    @DisplayName("the order of the given set does not change the joined value")
    void orderOfTheGivenSetDoesNotMatter() {
        Set<InitialAdminRescueCondition> reversed = new LinkedHashSet<>(List.of(
                InitialAdminRescueCondition.PASSWORD,
                InitialAdminRescueCondition.NO_ADMIN,
                InitialAdminRescueCondition.SUSPENDED));
        Set<InitialAdminRescueCondition> twoReversed = new LinkedHashSet<>(
                List.of(InitialAdminRescueCondition.PASSWORD, InitialAdminRescueCondition.SUSPENDED));

        assertThat(InitialAdminRescueCondition.code(reversed)).isEqualTo("SUSPENDED+NO_ADMIN+PASSWORD");
        assertThat(InitialAdminRescueCondition.code(twoReversed)).isEqualTo("SUSPENDED+PASSWORD");
    }

    @Test
    @DisplayName("an empty, missing or null-containing set is rejected")
    void emptyOrNullIsRejected() {
        assertThatThrownBy(() -> InitialAdminRescueCondition.code(Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InitialAdminRescueCondition.code(null)).isInstanceOf(NullPointerException.class);
        Set<InitialAdminRescueCondition> withNull =
                new HashSet<>(Arrays.asList(InitialAdminRescueCondition.PASSWORD, null));
        assertThatThrownBy(() -> InitialAdminRescueCondition.code(withNull)).isInstanceOf(NullPointerException.class);
    }

    @Property
    @Label("every non-empty subset fits the column, keeps the declared order and splits back to the same set")
    void everyNonEmptySubset(
            @ForAll boolean suspended, @ForAll boolean noAdmin, @ForAll boolean password, @ForAll boolean reverse) {
        List<InitialAdminRescueCondition> given = new ArrayList<>();
        if (suspended) {
            given.add(InitialAdminRescueCondition.SUSPENDED);
        }
        if (noAdmin) {
            given.add(InitialAdminRescueCondition.NO_ADMIN);
        }
        if (password) {
            given.add(InitialAdminRescueCondition.PASSWORD);
        }
        Assume.that(!given.isEmpty());
        if (reverse) {
            given = given.reversed();
        }
        Set<InitialAdminRescueCondition> conditions = new LinkedHashSet<>(given);

        String code = InitialAdminRescueCondition.code(conditions);

        assertThat(code.length()).isLessThanOrEqualTo(32);
        List<InitialAdminRescueCondition> parts = Arrays.stream(code.split("\\+"))
                .map(InitialAdminRescueCondition::valueOf)
                .toList();
        assertThat(parts).isSortedAccordingTo(Enum::compareTo).doesNotHaveDuplicates();
        assertThat(EnumSet.copyOf(parts)).isEqualTo(EnumSet.copyOf(conditions));
    }

    @Test
    @DisplayName("the rescued event keeps an unmodifiable copy of the conditions in the declared order")
    void rescuedEventCopiesTheConditions() {
        Set<InitialAdminRescueCondition> given = new LinkedHashSet<>(
                List.of(InitialAdminRescueCondition.PASSWORD, InitialAdminRescueCondition.SUSPENDED));

        InitialAdminRescuedEvent event = new InitialAdminRescuedEvent(7L, given, OCCURRED_AT);
        given.add(InitialAdminRescueCondition.NO_ADMIN);

        assertThat(event.userId()).isEqualTo(7L);
        assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(event.conditions())
                .containsExactly(InitialAdminRescueCondition.SUSPENDED, InitialAdminRescueCondition.PASSWORD);
        assertThatThrownBy(() -> event.conditions().add(InitialAdminRescueCondition.NO_ADMIN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("the rescued event rejects empty or missing conditions and a missing time")
    void rescuedEventRejectsInvalidValues() {
        Set<InitialAdminRescueCondition> one = Set.of(InitialAdminRescueCondition.NO_ADMIN);
        Set<InitialAdminRescueCondition> withNull =
                new HashSet<>(Arrays.asList(InitialAdminRescueCondition.NO_ADMIN, null));

        assertThatThrownBy(() -> new InitialAdminRescuedEvent(1L, Set.of(), OCCURRED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InitialAdminRescuedEvent(1L, null, OCCURRED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InitialAdminRescuedEvent(1L, withNull, OCCURRED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InitialAdminRescuedEvent(1L, one, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the created event keeps its values and rejects a missing time")
    void createdEvent() {
        InitialAdminCreatedEvent event = new InitialAdminCreatedEvent(3L, OCCURRED_AT);

        assertThat(event.userId()).isEqualTo(3L);
        assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThatThrownBy(() -> new InitialAdminCreatedEvent(3L, null)).isInstanceOf(NullPointerException.class);
    }
}
