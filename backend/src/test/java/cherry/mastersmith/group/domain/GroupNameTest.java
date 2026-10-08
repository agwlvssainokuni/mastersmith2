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
package cherry.mastersmith.group.domain;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.group.domain.GroupNameValidation.Reason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** グループの名前の値（BR1.1〜BR1.5、AC2.1.2、NFR1.11）の単体テスト。 */
class GroupNameTest {

    /** サロゲートペアで表される文字（1 コードポイント、Java の文字列の長さは 2）。 */
    private static final String EMOJI = "😀";

    private static GroupName valid(String raw) {
        GroupNameValidation result = GroupName.parse(raw);
        assertThat(result).isInstanceOf(GroupNameValidation.Valid.class);
        return ((GroupNameValidation.Valid) result).name();
    }

    private static Reason invalid(String raw) {
        GroupNameValidation result = GroupName.parse(raw);
        assertThat(result).isInstanceOf(GroupNameValidation.Invalid.class);
        return ((GroupNameValidation.Invalid) result).reason();
    }

    @Test
    @DisplayName("64 code points are accepted and 65 are refused, also when every code point is a surrogate pair")
    void lengthBoundaryInCodePoints() {
        assertThat(valid("a".repeat(64)).value()).hasSize(64);
        assertThat(invalid("a".repeat(65))).isEqualTo(Reason.INVALID_TOO_LONG);
        GroupName surrogates = valid(EMOJI.repeat(64));
        assertThat(surrogates.value()).hasSize(128);
        assertThat(surrogates.value().codePointCount(0, surrogates.value().length()))
                .isEqualTo(64);
        assertThat(invalid(EMOJI.repeat(65))).isEqualTo(Reason.INVALID_TOO_LONG);
        assertThat(valid("営" + EMOJI.repeat(63)).value().codePoints().count()).isEqualTo(64);
    }

    @Test
    @DisplayName("the length is counted after trimming, so surrounding spaces do not make a name too long")
    void lengthAfterTrimming() {
        assertThat(valid("  " + "a".repeat(64) + "　").value()).isEqualTo("a".repeat(64));
    }

    @Test
    @DisplayName("leading and trailing white space including full-width spaces is trimmed and inner spaces are kept")
    void trimming() {
        assertThat(valid("  営業部　").value()).isEqualTo("営業部");
        assertThat(valid(" Sales ").value()).isEqualTo("Sales");
        assertThat(valid("営業  第一 部").value()).isEqualTo("営業  第一 部");
        assertThat(valid("\t営業部\n").value()).isEqualTo("営業部");
    }

    @Test
    @DisplayName("null, empty and white-space-only names including full-width spaces are blank")
    void blankNames() {
        assertThat(invalid(null)).isEqualTo(Reason.INVALID_BLANK);
        assertThat(invalid("")).isEqualTo(Reason.INVALID_BLANK);
        assertThat(invalid("   ")).isEqualTo(Reason.INVALID_BLANK);
        assertThat(invalid("　　")).isEqualTo(Reason.INVALID_BLANK);
        assertThat(invalid(" \t\n ")).isEqualTo(Reason.INVALID_BLANK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"営業\n部", "営業\t部", "営業\r部", "営業\u0000部", "営業\u0007部", "営業\u007F部", "営業 部"})
    @DisplayName("an inner line break, tab or other control character is refused")
    void controlCharacters(String raw) {
        assertThat(invalid(raw)).isEqualTo(Reason.INVALID_CONTROL_CHARACTER);
    }

    @Test
    @DisplayName("the reasons are checked in the order blank, too long, control character")
    void reasonOrder() {
        assertThat(invalid("\u0007".repeat(65))).isEqualTo(Reason.INVALID_TOO_LONG);
        assertThat(invalid("a\u0007")).isEqualTo(Reason.INVALID_CONTROL_CHARACTER);
    }

    @Test
    @DisplayName("names that differ only in upper and lower case share the key, full-width letters do not")
    void keyIgnoresCaseOnly() {
        String key = valid("sales").key();

        assertThat(valid("Sales").key()).isEqualTo(key);
        assertThat(valid("SALES").key()).isEqualTo(key);
        assertThat(valid(" SaLeS ").key()).isEqualTo(key);
        assertThat(valid("Ｓａｌｅｓ").key()).isNotEqualTo(key).isEqualTo("ｓａｌｅｓ");
        assertThat(valid("営業部").key()).isEqualTo("営業部");
    }

    @Test
    @DisplayName("the key uses the locale-independent lower case")
    void keyIsLocaleIndependent() {
        assertThat(valid("TITLE").key()).isEqualTo("title");
        assertThat(valid("İ").key()).isEqualTo("i̇");
    }

    @Test
    @DisplayName("a rename to the literally same name is no change, a case-only change is a change")
    void sameAs() {
        GroupName name = valid("Sales");

        assertThat(name.sameAs("Sales")).isTrue();
        assertThat(name.sameAs("SALES")).isFalse();
        assertThat(valid(" Sales ").sameAs("Sales")).isTrue();
    }

    @Test
    @DisplayName("equal values are equal names and the string form is the value")
    void valueSemantics() {
        assertThat(valid("営業部")).isEqualTo(valid(" 営業部 ")).hasSameHashCodeAs(valid("営業部"));
        assertThat(valid("営業部")).isNotEqualTo(valid("営業一部"));
        assertThat(valid("営業部").toString()).isEqualTo("営業部");
    }
}
