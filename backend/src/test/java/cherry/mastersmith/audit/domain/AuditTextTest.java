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
package cherry.mastersmith.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 切り詰め（BR2.1、NFR3.4）の性質と境界のテスト。 */
class AuditTextTest {

    /** サロゲートペアで表される文字（絵文字）。 */
    private static final String EMOJI = "😀";

    @Property
    @Label("a truncated value never exceeds the limit in code points")
    void neverExceedsTheLimit(
            @ForAll @StringLength(min = 0, max = 200) String value, @ForAll @IntRange(min = 1, max = 64) int limit) {
        String truncated = AuditText.truncate(value, limit);

        assertThat(truncated.codePointCount(0, truncated.length())).isLessThanOrEqualTo(limit);
    }

    @Property
    @Label("a truncated value is a prefix of the original value")
    void isAPrefixOfTheOriginal(
            @ForAll @StringLength(min = 0, max = 200) String value, @ForAll @IntRange(min = 1, max = 64) int limit) {
        assertThat(value).startsWith(AuditText.truncate(value, limit));
    }

    @Property
    @Label("a truncated value never ends with a broken surrogate pair")
    void neverBreaksASurrogatePair(
            @ForAll @IntRange(min = 0, max = 40) int emojiCount, @ForAll @IntRange(min = 1, max = 40) int limit) {
        String value = EMOJI.repeat(emojiCount) + "あ" + EMOJI.repeat(emojiCount);

        String truncated = AuditText.truncate(value, limit);

        // 対になっていないサロゲートは、コードポイントとして読むとサロゲートの値のまま現れる。
        assertThat(truncated.codePoints().anyMatch(codePoint -> codePoint >= 0xD800 && codePoint <= 0xDFFF))
                .isFalse();
    }

    @Property
    @Label("a value already within the limit is returned unchanged")
    void shortValuesAreUnchanged(@ForAll @StringLength(min = 0, max = 30) String value) {
        assertThat(AuditText.truncate(value, 30)).isEqualTo(value);
    }

    @Test
    @DisplayName("an email address of 300 characters keeps its first 254 characters")
    void longEmailKeepsTheFirst254() {
        String email = "あ".repeat(290) + "@example.com";

        String truncated = AuditText.enteredEmail(email);

        assertThat(truncated).hasSize(AuditText.MAX_ENTERED_EMAIL_LENGTH).isEqualTo(email.substring(0, 254));
    }

    @Test
    @DisplayName("a request path of 600 characters keeps its first 512 characters")
    void longRequestPathKeepsTheFirst512() {
        String path = "/api/admin/" + "a".repeat(600);

        String truncated = AuditText.requestPath(path);

        assertThat(truncated).hasSize(AuditText.MAX_REQUEST_PATH_LENGTH).isEqualTo(path.substring(0, 512));
    }

    @Test
    @DisplayName("a user agent whose 512th character is a surrogate pair is cut before that character")
    void userAgentCutBeforeASurrogatePair() {
        // 511 文字のあとに絵文字を置くと、512 文字目がサロゲートペアになる。
        String userAgent = "u".repeat(511) + EMOJI + "tail";

        String truncated = AuditText.userAgent(userAgent);

        // 512 コードポイント（= 511 文字 + 絵文字1文字）で、char の数は 513 になる。
        assertThat(truncated.codePointCount(0, truncated.length())).isEqualTo(512);
        assertThat(truncated).hasSize(513).endsWith(EMOJI);

        // 上限が 511 のときは絵文字が入らず、その手前で切れる（壊れた文字を残さない）。
        String shorter = AuditText.truncate(userAgent, 511);
        assertThat(shorter).hasSize(511).isEqualTo("u".repeat(511));
    }

    @Test
    @DisplayName("null and blank values pass through and control characters are not rewritten")
    void nullBlankAndControlCharacters() {
        assertThat(AuditText.enteredEmail(null)).isNull();
        assertThat(AuditText.userAgent(null)).isNull();
        assertThat(AuditText.requestPath(null)).isNull();
        assertThat(AuditText.enteredEmail("")).isEmpty();

        String withControlCharacters = "  Mozilla/5.0\n\r\t改行を含む  ";
        assertThat(AuditText.userAgent(withControlCharacters)).isEqualTo(withControlCharacters);
    }

    @Test
    @DisplayName("a limit below one is rejected")
    void limitMustBePositive() {
        assertThatThrownBy(() -> AuditText.truncate("value", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
