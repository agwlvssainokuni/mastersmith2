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

import cherry.mastersmith.role.domain.PermissionTargetName.Reason;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 権限の設定の対象の名前の検証（BR4.3）の単体テスト。 */
class PermissionTargetNameTest {

    private static final String EMOJI = "😀";

    @Test
    @DisplayName("128 code points are accepted and 129 are refused, also with surrogate pairs")
    void lengthBoundary() {
        assertThat(PermissionTargetName.problemOf("a".repeat(128))).isEmpty();
        assertThat(PermissionTargetName.problemOf("a".repeat(129))).contains(Reason.TOO_LONG);
        assertThat(PermissionTargetName.problemOf(EMOJI.repeat(128))).isEmpty();
        assertThat(PermissionTargetName.problemOf(EMOJI.repeat(129))).contains(Reason.TOO_LONG);
    }

    @Test
    @DisplayName("null and empty names are blank, but white space is kept and counted as a name")
    void blankAndSpaces() {
        assertThat(PermissionTargetName.problemOf((String) null)).contains(Reason.BLANK);
        assertThat(PermissionTargetName.problemOf("")).contains(Reason.BLANK);
        assertThat(PermissionTargetName.problemOf(" ")).isEmpty();
        assertThat(PermissionTargetName.problemOf(" SALES ")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"A\nB", "A\tB", "A\u0000B", "A\u007FB", "A B", "A B"})
    @DisplayName("a control character or a line separator is refused")
    void controlCharacters(String name) {
        assertThat(PermissionTargetName.problemOf(name)).contains(Reason.CONTROL_CHARACTER);
    }

    @Test
    @DisplayName("symbols, dots and percent signs are ordinary characters, and a format character is accepted")
    void symbolsAreAccepted() {
        assertThat(PermissionTargetName.problemOf("a/b..c;d%e")).isEmpty();
        assertThat(PermissionTargetName.problemOf("A​B")).isEmpty();
    }

    @Test
    @DisplayName("a target reports the first wrong name from the schema down to the column")
    void targetNames() {
        assertThat(PermissionTargetName.problemOf(PermissionTarget.column("S", "T", "C")))
                .isEmpty();
        assertThat(PermissionTargetName.problemOf(PermissionTarget.schema("S\n")))
                .contains(Reason.CONTROL_CHARACTER);
        assertThat(PermissionTargetName.problemOf(PermissionTarget.table("S", "T".repeat(129))))
                .contains(Reason.TOO_LONG);
        assertThat(PermissionTargetName.problemOf(PermissionTarget.column("S", "T", "C\t")))
                .isEqualTo(Optional.of(Reason.CONTROL_CHARACTER));
    }
}
