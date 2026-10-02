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

import java.util.Locale;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 一覧の検索の文字（BR1.3〜BR1.5、R-07）の単体テスト。 */
class SearchTextTest {

    /** パターンから前後の % を外し、エスケープを戻した値。 */
    private static String unescape(String pattern) {
        String body = pattern.substring(1, pattern.length() - 1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\') {
                i++;
                c = body.charAt(i);
            }
            out.append(c);
        }
        return out.toString();
    }

    @Test
    @DisplayName("leading and trailing half-width and full-width spaces are removed but inner spaces stay")
    void stripsOuterSpaces() {
        SearchText text = new SearchText(" 　Taro Yamada\t　");

        assertThat(text.isBlank()).isFalse();
        assertThat(text.likePattern().value()).isEqualTo("%taro yamada%");
    }

    @Test
    @DisplayName("spaces only and the empty string are blank (no search)")
    void blank() {
        assertThat(new SearchText("").isBlank()).isTrue();
        assertThat(new SearchText(" 　\t").isBlank()).isTrue();
        assertThat(new SearchText(" a ").isBlank()).isFalse();
    }

    @Test
    @DisplayName("254 code points after stripping are accepted and 255 are too long, counting surrogate pairs as one")
    void lengthBoundary() {
        String surrogate = "𠮷"; // 𠮷（サロゲートペア）
        assertThat(new SearchText(" " + surrogate.repeat(254) + " ").isTooLong())
                .isFalse();
        assertThat(new SearchText(surrogate.repeat(255)).isTooLong()).isTrue();
        assertThat(new SearchText("a".repeat(254)).isTooLong()).isFalse();
        assertThat(new SearchText("a".repeat(255)).isTooLong()).isTrue();
    }

    @Test
    @DisplayName("percent, underscore and backslash are escaped with a backslash")
    void escapesWildcards() {
        assertThat(new SearchText("100% off_sale\\x").likePattern().value()).isEqualTo("%100\\% off\\_sale\\\\x%");
    }

    @Test
    @DisplayName("lower-casing uses Locale.ROOT even when the default locale is Turkish")
    void rootLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(new SearchText("ISTANBUL").likePattern().value()).isEqualTo("%istanbul%");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("full-width and accented letters are lower-cased")
    void unicodeLowerCase() {
        assertThat(new SearchText("ＡＢＣ ÉCOLE").likePattern().value()).isEqualTo("%ａｂｃ école%");
    }

    @Test
    @DisplayName("toString and the pattern's toString never show the value")
    void redacted() {
        SearchText text = new SearchText("leak-check@example.com");

        assertThat(text.toString()).doesNotContain("leak").doesNotContain("example");
        assertThat(text.likePattern().toString()).isEqualTo("***");
    }

    @Test
    @DisplayName("a null value is rejected")
    void nullRejected() {
        assertThatThrownBy(() -> new SearchText(null)).isInstanceOf(NullPointerException.class);
    }

    @Property
    @Label("inside the pattern every percent and underscore is escaped")
    void noUnescapedWildcards(@ForAll String raw) {
        String pattern = new SearchText(raw).likePattern().value();
        String body = pattern.substring(1, pattern.length() - 1);

        assertThat(pattern).startsWith("%").endsWith("%");
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\') {
                assertThat(i + 1).isLessThan(body.length());
                assertThat(body.charAt(i + 1)).isIn('\\', '%', '_');
                i++;
            } else {
                assertThat(c).isNotIn('%', '_');
            }
        }
    }

    @Property
    @Label("unescaping the pattern gives the stripped value lower-cased with Locale.ROOT")
    void roundTrip(@ForAll String raw) {
        String expected = cherry.mastersmith.user.domain.DisplayName.strip(raw).toLowerCase(Locale.ROOT);

        assertThat(unescape(new SearchText(raw).likePattern().value())).isEqualTo(expected);
    }
}
