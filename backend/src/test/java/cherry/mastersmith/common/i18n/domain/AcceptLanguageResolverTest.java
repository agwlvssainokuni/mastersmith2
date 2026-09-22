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
package cherry.mastersmith.common.i18n.domain;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AcceptLanguageResolverTest {

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @DisplayName("regional tags are judged by their primary language")
    @CsvSource(
            delimiter = '|',
            value = {"en-US|EN", "ja-JP|JA", "EN-gb|EN", "ja|JA", "en|EN"})
    void regionalTags(String header, DisplayLanguage expected) {
        assertThat(AcceptLanguageResolver.resolve(header)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @DisplayName("languages are ordered by quality value and then by written order")
    @CsvSource(
            delimiter = '|',
            value = {
                "ja-JP,en;q=0.8|JA",
                "ja;q=0.5,en;q=0.9|EN",
                "en;q=0.5,ja;q=0.5|EN",
                "fr,en;q=0.7,ja;q=0.6|EN",
                "fr;q=1.0,ja;q=0.1|JA"
            })
    void qualityOrdering(String header, DisplayLanguage expected) {
        assertThat(AcceptLanguageResolver.resolve(header)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @DisplayName("languages with q=0 are excluded")
    @CsvSource(
            delimiter = '|',
            value = {"en;q=0,ja;q=0.1|JA", "en;q=0.000|JA", "ja;q=0,en;q=0.2|EN"})
    void zeroQualityExcluded(String header, DisplayLanguage expected) {
        assertThat(AcceptLanguageResolver.resolve(header)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> JA")
    @DisplayName("missing, malformed or unmatched headers fall back to Japanese")
    @NullAndEmptySource
    @ValueSource(strings = {"fr", "*", "de-DE,fr;q=0.9", " , ;", "en;q=abc", "en;q=1.5", "12345", "en_US"})
    void fallsBackToJapanese(String header) {
        assertThat(AcceptLanguageResolver.resolve(header)).isEqualTo(DisplayLanguage.JA);
    }

    @Property
    @Label("any header value resolves to Japanese or English without throwing")
    void anyHeaderResolves(@ForAll String header) {
        assertThat(AcceptLanguageResolver.resolve(header)).isIn(DisplayLanguage.JA, DisplayLanguage.EN);
    }

    @Property
    @Label("a header listing only English with a positive quality always resolves to English")
    void englishOnlyResolvesToEnglish(@ForAll("positiveQuality") String quality) {
        assertThat(AcceptLanguageResolver.resolve("en;q=" + quality)).isEqualTo(DisplayLanguage.EN);
    }

    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<String> positiveQuality() {
        return net.jqwik.api.Arbitraries.integers()
                .between(1, 1000)
                .map(n -> n == 1000 ? "1" : "0." + String.format("%03d", n));
    }
}
