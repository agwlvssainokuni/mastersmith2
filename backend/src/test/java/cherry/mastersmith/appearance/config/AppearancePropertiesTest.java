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
package cherry.mastersmith.appearance.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.validation.annotation.Validated;

/** 見た目の設定の設定の型の結び付け（BR1.7、NFR9.1）。Spring を起動せず、Spring Boot の {@link Binder} で結び付ける。 */
class AppearancePropertiesTest {

    private static AppearanceProperties bind(Map<String, String> values) {
        Binder binder = new Binder(new MapConfigurationPropertySource(values));
        return binder.bindOrCreate(AppearanceProperties.PREFIX, AppearanceProperties.class);
    }

    @Test
    @DisplayName("both items are bound as the plain strings that were set")
    void bindsBothItemsAsStrings() {
        AppearanceProperties properties =
                bind(Map.of(AppearanceProperties.BRAND_COLOR, " Green ", AppearanceProperties.FONT_FAMILY, "Serif"));

        assertThat(properties.brandColor()).isEqualTo(" Green ");
        assertThat(properties.fontFamily()).isEqualTo("Serif");
    }

    @Test
    @DisplayName("missing items are bound as null without failing")
    void missingItemsAreNull() {
        AppearanceProperties properties = bind(Map.of());

        assertThat(properties.brandColor()).isNull();
        assertThat(properties.fontFamily()).isNull();
    }

    @Test
    @DisplayName("empty items are bound as null or empty without failing")
    void emptyItemsAreNullOrEmpty() {
        AppearanceProperties properties =
                bind(Map.of(AppearanceProperties.BRAND_COLOR, "", AppearanceProperties.FONT_FAMILY, ""));

        assertThat(properties.brandColor()).isNullOrEmpty();
        assertThat(properties.fontFamily()).isNullOrEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("any string, even one that is not allowed, is bound unchanged without failing")
    @ValueSource(strings = {"red", "mono", "blue-ish", "ｂｌｕｅ", "<b>\"&'</b>", "appearance-leak-check"})
    void anyStringIsBound(String value) {
        AppearanceProperties properties =
                bind(Map.of(AppearanceProperties.BRAND_COLOR, value, AppearanceProperties.FONT_FAMILY, value));

        assertThat(properties.brandColor()).isEqualTo(value);
        assertThat(properties.fontFamily()).isEqualTo(value);
    }

    @Test
    @DisplayName("the items are also bound from environment variable names")
    void bindsFromEnvironmentVariableNames() {
        SystemEnvironmentPropertySource environment = new SystemEnvironmentPropertySource(
                "env",
                Map.of("MASTERSMITH_APPEARANCE_BRAND_COLOR", "purple", "MASTERSMITH_APPEARANCE_FONT_FAMILY", "serif"));
        Binder binder = new Binder(ConfigurationPropertySources.from(environment));

        AppearanceProperties properties = binder.bindOrCreate(AppearanceProperties.PREFIX, AppearanceProperties.class);

        assertThat(properties.brandColor()).isEqualTo("purple");
        assertThat(properties.fontFamily()).isEqualTo("serif");
    }

    @Test
    @DisplayName("the properties type carries no validation that could stop the start")
    void hasNoValidation() {
        assertThat(AppearanceProperties.class.isAnnotationPresent(Validated.class))
                .isFalse();
    }
}
