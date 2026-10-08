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
package cherry.mastersmith.role.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 有効な作業ロールの決め方（BR7.1・BR7.2・BR7.4、AC4.1.14〜AC4.1.16）の単体テスト。 */
class WorkRoleChooserTest {

    @Test
    @DisplayName("the stored work role is used while it is one of the user's roles")
    void storedRoleInTheSet() {
        assertThat(WorkRoleChooser.choose(Set.of(3L, 7L, 9L), OptionalLong.of(7L)))
                .hasValue(7L);
    }

    @Test
    @DisplayName("without a stored work role the role with the smallest id is the first role")
    void firstRoleWithoutStored() {
        assertThat(WorkRoleChooser.choose(List.of(9L, 3L, 7L), OptionalLong.empty()))
                .hasValue(3L);
    }

    @Test
    @DisplayName("a stored role outside the user's roles is read as the first role")
    void storedRoleOutsideTheSet() {
        assertThat(WorkRoleChooser.choose(Set.of(5L, 8L), OptionalLong.of(2L))).hasValue(5L);
    }

    @Test
    @DisplayName("a user without roles has no work role even if a stored role remains")
    void noRoles() {
        assertThat(WorkRoleChooser.choose(Set.of(), OptionalLong.of(4L))).isEmpty();
        assertThat(WorkRoleChooser.choose(Set.of(), OptionalLong.empty())).isEmpty();
    }

    @Test
    @DisplayName("the stored role comes back when it is assigned again")
    void storedRoleComesBack() {
        OptionalLong stored = OptionalLong.of(6L);

        assertThat(WorkRoleChooser.choose(Set.of(2L), stored)).hasValue(2L);
        assertThat(WorkRoleChooser.choose(Set.of(2L, 6L), stored)).hasValue(6L);
    }

    @Test
    @DisplayName("the same role reached directly and through a group counts once")
    void duplicateRoleIds() {
        assertThat(WorkRoleChooser.choose(List.of(4L, 4L, 11L), OptionalLong.empty()))
                .hasValue(4L);
    }

    @Test
    @DisplayName("null arguments are rejected")
    void nullArguments() {
        assertThatThrownBy(() -> WorkRoleChooser.choose(null, OptionalLong.empty()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkRoleChooser.choose(Set.of(1L), null)).isInstanceOf(NullPointerException.class);
    }
}
