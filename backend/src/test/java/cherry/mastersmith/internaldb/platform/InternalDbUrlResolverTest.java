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
package cherry.mastersmith.internaldb.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

/** 内部DB の種類の判断（BR1.1・BR1.3・BR1.4。T1）。 */
class InternalDbUrlResolverTest {

    @ParameterizedTest(name = "[{index}] {0} is {1}")
    @CsvSource(delimiter = '|', textBlock = """
                    jdbc:h2:file:./data/example;DEFRAG_ALWAYS=TRUE | H2
                    jdbc:h2:mem:example | H2
                    jdbc:postgresql://db.example.com:5432/example | POSTGRESQL
                    jdbc:postgresql: | POSTGRESQL
                    """)
    @DisplayName("resolves H2 and PostgreSQL by the URL prefix")
    void resolvesSupportedKinds(String url, InternalDbKind expected) {
        assertThat(InternalDbUrlResolver.resolve(url)).isEqualTo(new InternalDbUrlResolution.Supported(expected));
    }

    @ParameterizedTest(name = "[{index}] unsupported: {0}")
    @ValueSource(
            strings = {
                "jdbc:mysql://db.example.com:3306/example",
                "jdbc:mariadb://db.example.com:3306/example",
                "jdbc:POSTGRESQL://db.example.com/example",
                "jdbc:H2:mem:example",
                " jdbc:postgresql://db.example.com/example",
                "jdbc:",
                "postgresql://db.example.com/example"
            })
    @DisplayName("treats other kinds, different letter case and a leading space as unsupported")
    void resolvesUnsupported(String url) {
        assertThat(InternalDbUrlResolver.resolve(url)).isEqualTo(new InternalDbUrlResolution.Unsupported());
    }

    @ParameterizedTest(name = "[{index}] empty: [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("treats null, empty and blank values as empty")
    void resolvesEmpty(String url) {
        assertThat(InternalDbUrlResolver.resolve(url)).isEqualTo(new InternalDbUrlResolution.Empty());
    }

    @Test
    @DisplayName("settings read the property once and keep only the resolution")
    void settingsFromEnvironment() {
        MockEnvironment env = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://x/y");

        InternalDbSettings settings = InternalDbSettings.from(env);

        assertThat(settings.resolution()).contains(new InternalDbUrlResolution.Supported(InternalDbKind.POSTGRESQL));
        assertThat(settings.kind()).contains(InternalDbKind.POSTGRESQL);
        assertThat(settings.toString()).doesNotContain("postgresql://x/y");
    }

    @Test
    @DisplayName("settings make no judgement when the property is absent from the environment")
    void settingsWithoutProperty() {
        InternalDbSettings settings = InternalDbSettings.from(new MockEnvironment());

        assertThat(settings.resolution()).isEmpty();
        assertThat(settings.kind()).isEmpty();
    }

    @Test
    @DisplayName("settings report an empty value as Empty and an unsupported value without a kind")
    void settingsEmptyAndUnsupported() {
        InternalDbSettings empty =
                InternalDbSettings.from(new MockEnvironment().withProperty("spring.datasource.url", ""));
        InternalDbSettings unsupported = InternalDbSettings.from(
                new MockEnvironment().withProperty("spring.datasource.url", "jdbc:mysql://x/y"));

        assertThat(empty.resolution()).contains(new InternalDbUrlResolution.Empty());
        assertThat(empty.kind()).isEqualTo(Optional.empty());
        assertThat(unsupported.resolution()).contains(new InternalDbUrlResolution.Unsupported());
        assertThat(unsupported.kind()).isEmpty();
    }
}
