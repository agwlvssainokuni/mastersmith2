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

import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Pattern;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 氏名の決まり（BR1.1〜BR1.6、NFR9.2）の単体テストと性質ベースのテスト。
 *
 * <p>このパッケージには氏名の決まり {@link DisplayName} があるため、JUnit の説明の注釈は完全な名前で書く。失敗時の乱数の種は jqwik の
 * 失敗の報告に出る（{@code backend/src/test/resources/junit-platform.properties}）。
 */
class DisplayNameTest {

    private static final Pattern WHITE_SPACE = Pattern.compile("\\p{IsWhite_Space}");

    private static final String PADDING = " 　\t\n ";

    @Test
    @org.junit.jupiter.api.DisplayName(
            "leading and trailing half-width, full-width, tab and no-break spaces are removed")
    void stripsSurroundingWhiteSpace() {
        assertThat(DisplayName.strip(PADDING + "山田 花子" + PADDING)).isEqualTo("山田 花子");
        assertThat(DisplayName.check(PADDING + "山田 花子" + PADDING)).isEmpty();
        assertThat(DisplayName.requireValid("\t山田　花子 ")).isEqualTo("山田　花子");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("inner spaces are kept as they are and never collapsed")
    void keepsInnerSpaces() {
        assertThat(DisplayName.strip(" 山田  花子 ")).isEqualTo("山田  花子");
        assertThat(DisplayName.strip("山田　花子")).isEqualTo("山田　花子");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("empty, blank-only and null names are REQUIRED")
    void emptyIsRequired() {
        assertThat(DisplayName.check(null)).contains(FieldErrorReason.REQUIRED);
        assertThat(DisplayName.check("")).contains(FieldErrorReason.REQUIRED);
        assertThat(DisplayName.check(PADDING)).contains(FieldErrorReason.REQUIRED);
        assertThat(DisplayName.strip(null)).isNull();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("254 code points pass and 255 are TOO_LONG, counted as code points")
    void lengthBoundary() {
        assertThat(DisplayName.check("a".repeat(254))).isEmpty();
        assertThat(DisplayName.check("a".repeat(255))).contains(FieldErrorReason.TOO_LONG);
        assertThat(DisplayName.check("😀".repeat(254))).isEmpty();
        assertThat(DisplayName.check("😀".repeat(255))).contains(FieldErrorReason.TOO_LONG);
        assertThat(DisplayName.check(" " + "あ".repeat(254) + " "))
                .as("除いた後で数える")
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\t", "\u0000", "​", "‍", "‮", "﻿", "\u0085"})
    @org.junit.jupiter.api.DisplayName("an inner control or invisible format character is INVALID_CHARACTER")
    void innerControlOrFormatIsRejected(String character) {
        assertThat(DisplayName.check("山田" + character + "花子")).contains(FieldErrorReason.INVALID_CHARACTER);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("the first failing rule wins: TOO_LONG before INVALID_CHARACTER")
    void firstRuleWins() {
        assertThat(DisplayName.check("a".repeat(254) + "​")).contains(FieldErrorReason.TOO_LONG);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("names are never Unicode-normalized")
    void noNormalization() {
        String decomposed = "é";
        assertThat(Normalizer.isNormalized(decomposed, Normalizer.Form.NFC)).isFalse();
        assertThat(DisplayName.requireValid(decomposed)).isEqualTo(decomposed);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("requireValid rejects an invalid name without echoing its value")
    void requireValidRejects() {
        String secretLike = "漏れてはいけない氏名​";
        assertThatThrownBy(() -> DisplayName.requireValid(secretLike))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID_CHARACTER")
                .hasMessageNotContaining("漏れてはいけない氏名");
        assertThatThrownBy(() -> DisplayName.requireValid(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "the white-space set agrees with the Unicode White_Space property for every code point")
    void whiteSpaceAgreesWithUnicode() {
        for (int codePoint = 0; codePoint <= Character.MAX_CODE_POINT; codePoint++) {
            boolean expected =
                    WHITE_SPACE.matcher(Character.toString(codePoint)).matches();
            assertThat(DisplayName.isWhiteSpace(codePoint))
                    .as("U+%04X", codePoint)
                    .isEqualTo(expected);
        }
    }

    @Property
    @Label("an accepted name has no surrounding white space, 1 to 254 code points and no Cc or Cf characters")
    void acceptedNamesSatisfyTheRules(
            @ForAll @StringLength(max = 300) String core, @ForAll @IntRange(max = 3) int pad) {
        String raw = " ".repeat(pad) + core + "　".repeat(pad);
        Optional<FieldErrorReason> reason = DisplayName.check(raw);
        if (reason.isEmpty()) {
            String value = DisplayName.requireValid(raw);
            int length = value.codePointCount(0, value.length());
            assertThat(length).isBetween(1, DisplayName.MAX_CODE_POINTS);
            assertThat(DisplayName.isWhiteSpace(value.codePointAt(0))).isFalse();
            assertThat(DisplayName.isWhiteSpace(value.codePointBefore(value.length())))
                    .isFalse();
            assertThat(value.codePoints())
                    .noneMatch(cp ->
                            Character.getType(cp) == Character.CONTROL || Character.getType(cp) == Character.FORMAT);
        }
    }

    @Property
    @Label("judging is deterministic and idempotent: the same input twice and the stripped value give the same result")
    void judgingIsIdempotent(@ForAll String raw) {
        assertThat(DisplayName.check(raw)).isEqualTo(DisplayName.check(raw));
        String stripped = DisplayName.strip(raw);
        assertThat(DisplayName.strip(stripped)).isEqualTo(stripped);
        assertThat(DisplayName.check(stripped)).isEqualTo(DisplayName.check(raw));
    }

    @Property
    @Label("an accepted value judged again stays the same value")
    void acceptedValueIsStable(@ForAll @StringLength(min = 1, max = 254) String raw) {
        if (DisplayName.check(raw).isEmpty()) {
            String value = DisplayName.requireValid(raw);
            assertThat(DisplayName.requireValid(value)).isEqualTo(value);
        }
    }
}
