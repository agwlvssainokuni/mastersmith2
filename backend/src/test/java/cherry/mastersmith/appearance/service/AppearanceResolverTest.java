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
package cherry.mastersmith.appearance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 見た目の設定の値の判定（BR1.1〜BR1.5、NFR9.2・NFR9.8）。判定の例は functional-spec.md の W1 の表。
 *
 * <p>性質ベースのテスト（jqwik）の失敗時の乱数の種は jqwik の報告に出る（{@code @Property(seed = "...")} に与えて再現する。
 * {@code src/test/resources/junit-platform.properties}）。
 */
class AppearanceResolverTest {

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @DisplayName("a missing, empty or blank value takes the default without a warning")
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t", "　", " \t　 "})
    void missingOrBlankTakesDefault(String raw) {
        assertThat(AppearanceResolver.resolveBrandColor(raw)).isEqualTo(new Resolution<>(BrandColor.BLUE, false));
        assertThat(AppearanceResolver.resolveFontFamily(raw)).isEqualTo(new Resolution<>(FontFamily.SANS, false));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @DisplayName("an allowed brand color is taken regardless of case and surrounding spaces")
    @ValueSource(strings = {"green", "Green", " green ", "GREEN", "\tgreen\t", "　GrEeN　"})
    void allowedBrandColorIgnoresCaseAndSpaces(String raw) {
        assertThat(AppearanceResolver.resolveBrandColor(raw)).isEqualTo(new Resolution<>(BrandColor.GREEN, false));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @DisplayName("an allowed font family is taken regardless of case and surrounding spaces")
    @ValueSource(strings = {"serif", "Serif", " SERIF ", "\tserif"})
    void allowedFontFamilyIgnoresCaseAndSpaces(String raw) {
        assertThat(AppearanceResolver.resolveFontFamily(raw)).isEqualTo(new Resolution<>(FontFamily.SERIF, false));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @DisplayName("a brand color that is not allowed takes blue with a warning")
    @ValueSource(strings = {"red", "blue-ish", "ｂｌｕｅ", "bl ue", "<blue>", "appearance-leak-check"})
    void disallowedBrandColorWarns(String raw) {
        assertThat(AppearanceResolver.resolveBrandColor(raw)).isEqualTo(new Resolution<>(BrandColor.BLUE, true));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @DisplayName("a font family that is not allowed takes sans with a warning")
    @ValueSource(strings = {"mono", "monospace", "serif-bold", "blue"})
    void disallowedFontFamilyWarns(String raw) {
        assertThat(AppearanceResolver.resolveFontFamily(raw)).isEqualTo(new Resolution<>(FontFamily.SANS, true));
    }

    @Test
    @DisplayName("the two items are judged separately")
    void itemsAreJudgedSeparately() {
        assertThat(AppearanceResolver.resolveBrandColor("red")).isEqualTo(new Resolution<>(BrandColor.BLUE, true));
        assertThat(AppearanceResolver.resolveFontFamily("Serif")).isEqualTo(new Resolution<>(FontFamily.SERIF, false));
        assertThat(AppearanceResolver.resolveFontFamily("mono")).isEqualTo(new Resolution<>(FontFamily.SANS, true));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("every allowed brand color passes")
    @EnumSource(BrandColor.class)
    void everyBrandColorPasses(BrandColor color) {
        assertThat(AppearanceResolver.resolveBrandColor(color.value())).isEqualTo(new Resolution<>(color, false));
        assertThat(AppearanceResolver.resolveBrandColor(color.name())).isEqualTo(new Resolution<>(color, false));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("every allowed font family passes")
    @EnumSource(FontFamily.class)
    void everyFontFamilyPasses(FontFamily family) {
        assertThat(AppearanceResolver.resolveFontFamily(family.value())).isEqualTo(new Resolution<>(family, false));
        assertThat(AppearanceResolver.resolveFontFamily(family.name())).isEqualTo(new Resolution<>(family, false));
    }

    @ParameterizedTest(name = "[{index}] {0} / {1}")
    @DisplayName("the result does not depend on the default locale of the runtime")
    @CsvSource({"tr-TR, SERIF, SERIF", "tr-TR, PURPLE, PURPLE", "az-AZ, SERIF, SERIF", "lt-LT, ORANGE, ORANGE"})
    void independentOfDefaultLocale(String languageTag, String raw, String expected) {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag(languageTag));
            if (Arrays.stream(FontFamily.values()).anyMatch(f -> f.name().equals(expected))) {
                assertThat(AppearanceResolver.resolveFontFamily(raw))
                        .isEqualTo(new Resolution<>(FontFamily.valueOf(expected), false));
            } else {
                assertThat(AppearanceResolver.resolveBrandColor(raw))
                        .isEqualTo(new Resolution<>(BrandColor.valueOf(expected), false));
            }
            assertThat(BrandColor.allowedValues()).isEqualTo("blue, green, purple, orange");
            assertThat(FontFamily.allowedValues()).isEqualTo("sans, serif");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("the lowercase names and the defaults match the contract C7")
    void namesAndDefaults() {
        assertThat(Arrays.stream(BrandColor.values()).map(BrandColor::value))
                .containsExactly("blue", "green", "purple", "orange");
        assertThat(Arrays.stream(FontFamily.values()).map(FontFamily::value)).containsExactly("sans", "serif");
        assertThat(BrandColor.defaultValue()).isEqualTo(BrandColor.BLUE);
        assertThat(FontFamily.defaultValue()).isEqualTo(FontFamily.SANS);
        assertThat(BrandColor.allowedValues()).isEqualTo("blue, green, purple, orange");
        assertThat(FontFamily.allowedValues()).isEqualTo("sans, serif");
    }

    // ---- 性質ベースのテスト（NFR9.8） ----

    /** 任意の文字列（空・空白・記号・全角・制御文字を含む）と null。 */
    @Provide
    Arbitrary<String> anyText() {
        Arbitrary<String> text = Arbitraries.strings()
                .withCharRange('\u0000', 'ɏ')
                .withChars('　', 'ｂ', 'ｌ', 'ｕ', 'ｅ', 'İ', 'ı', 'あ', '色')
                .ofMaxLength(24);
        return Arbitraries.oneOf(text, Arbitraries.just(null));
    }

    /** 許される値の大文字・小文字と前後の空白を揺らした文字列と、その値の組。 */
    @Provide
    Arbitrary<List<String>> shakenAllowedBrandColors() {
        Arbitrary<BrandColor> color = Arbitraries.of(BrandColor.class);
        Arbitrary<String> spaces = Arbitraries.of(' ', '\t', '　')
                .list()
                .ofMaxSize(3)
                .map(list -> {
                    StringBuilder builder = new StringBuilder();
                    list.forEach(builder::append);
                    return builder.toString();
                });
        Arbitrary<Long> caseMask = Arbitraries.longs().greaterOrEqual(0);
        return Combinators.combine(color, spaces, spaces, caseMask).as((c, before, after, mask) -> {
            StringBuilder shaken = new StringBuilder(before);
            String name = c.value();
            for (int i = 0; i < name.length(); i++) {
                char ch = name.charAt(i);
                shaken.append(((mask >> i) & 1) == 1 ? Character.toUpperCase(ch) : ch);
            }
            shaken.append(after);
            return List.of(shaken.toString(), c.name());
        });
    }

    @Property(tries = 500)
    void anyInputAlwaysGivesAnAllowedValue(@ForAll("anyText") String raw) {
        assertThatCode(() -> AppearanceResolver.resolveBrandColor(raw)).doesNotThrowAnyException();
        assertThatCode(() -> AppearanceResolver.resolveFontFamily(raw)).doesNotThrowAnyException();
        assertThat(AppearanceResolver.resolveBrandColor(raw).value())
                .isNotNull()
                .isIn((Object[]) BrandColor.values());
        assertThat(AppearanceResolver.resolveFontFamily(raw).value())
                .isNotNull()
                .isIn((Object[]) FontFamily.values());
    }

    @Property(tries = 500)
    void shakingCaseAndSpacesGivesTheSameValue(@ForAll("shakenAllowedBrandColors") List<String> shakenAndName) {
        Resolution<BrandColor> result = AppearanceResolver.resolveBrandColor(shakenAndName.get(0));

        assertThat(result).isEqualTo(new Resolution<>(BrandColor.valueOf(shakenAndName.get(1)), false));
    }

    @Property(tries = 500)
    void nonMatchingNonEmptyValueTakesDefaultWithWarning(@ForAll("anyText") String raw) {
        if (raw == null || raw.strip().isEmpty()) {
            return;
        }
        String normalized = raw.strip().toLowerCase(Locale.ROOT);
        boolean colorMatches =
                Arrays.stream(BrandColor.values()).anyMatch(c -> c.value().equals(normalized));
        boolean familyMatches =
                Arrays.stream(FontFamily.values()).anyMatch(f -> f.value().equals(normalized));

        if (!colorMatches) {
            assertThat(AppearanceResolver.resolveBrandColor(raw)).isEqualTo(new Resolution<>(BrandColor.BLUE, true));
        }
        if (!familyMatches) {
            assertThat(AppearanceResolver.resolveFontFamily(raw)).isEqualTo(new Resolution<>(FontFamily.SANS, true));
        }
    }
}
