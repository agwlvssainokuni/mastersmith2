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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.appearance.config.AppearanceProperties;
import cherry.mastersmith.common.testsupport.LogEvents;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;

/** 見た目の設定の起動時の解決と警告のログ（BR1.3〜BR1.6・BR2.1、NFR6.2・NFR9.4）。 */
class AppearanceServiceTest {

    private static final String LEAK_MARK_COLOR = "red-appearance-leak-check";

    private static final String LEAK_MARK_FAMILY = "mono-appearance-leak-check";

    private static List<ILoggingEvent> warnings(LogEvents events) {
        return events.list().stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .toList();
    }

    private static Map<String, Object> keyValues(ILoggingEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (pairs != null) {
            pairs.forEach(pair -> map.put(pair.key, pair.value));
        }
        return map;
    }

    private static String rendered(ILoggingEvent event) {
        return event.getFormattedMessage() + " " + keyValues(event);
    }

    @Test
    @DisplayName("without any setting the defaults are taken and nothing is warned")
    void defaultsWithoutSetting() {
        try (LogEvents events = LogEvents.capture(AppearanceService.class)) {
            AppearanceService service = new AppearanceService(new AppearanceProperties(null, null));

            assertThat(service.current()).isEqualTo(new ResolvedAppearance(BrandColor.BLUE, FontFamily.SANS));
            assertThat(warnings(events)).isEmpty();
        }
    }

    @Test
    @DisplayName("allowed values are taken and nothing is warned")
    void allowedValuesAreTaken() {
        try (LogEvents events = LogEvents.capture(AppearanceService.class)) {
            AppearanceService service = new AppearanceService(new AppearanceProperties(" Green ", "SERIF"));

            assertThat(service.current()).isEqualTo(new ResolvedAppearance(BrandColor.GREEN, FontFamily.SERIF));
            assertThat(warnings(events)).isEmpty();
        }
    }

    @Test
    @DisplayName("two disallowed values take the defaults and warn once per item")
    void twoDisallowedValuesWarnTwice() {
        try (LogEvents events = LogEvents.capture(AppearanceService.class)) {
            AppearanceService service =
                    new AppearanceService(new AppearanceProperties(LEAK_MARK_COLOR, LEAK_MARK_FAMILY));

            assertThat(service.current()).isEqualTo(new ResolvedAppearance(BrandColor.BLUE, FontFamily.SANS));
            List<ILoggingEvent> warnings = warnings(events);
            assertThat(warnings).hasSize(2);
            assertThat(keyValues(warnings.get(0)))
                    .containsExactly(
                            Map.entry("property", "mastersmith.appearance.brand-color"),
                            Map.entry("defaultValue", "blue"),
                            Map.entry("allowedValues", "blue, green, purple, orange"));
            assertThat(keyValues(warnings.get(1)))
                    .containsExactly(
                            Map.entry("property", "mastersmith.appearance.font-family"),
                            Map.entry("defaultValue", "sans"),
                            Map.entry("allowedValues", "sans, serif"));
        }
    }

    @Test
    @DisplayName("only the disallowed item is replaced and warned")
    void onlyTheDisallowedItemWarns() {
        try (LogEvents events = LogEvents.capture(AppearanceService.class)) {
            AppearanceService service = new AppearanceService(new AppearanceProperties("red", "Serif"));

            assertThat(service.current()).isEqualTo(new ResolvedAppearance(BrandColor.BLUE, FontFamily.SERIF));
            assertThat(warnings(events))
                    .singleElement()
                    .satisfies(event -> assertThat(keyValues(event))
                            .containsEntry("property", "mastersmith.appearance.brand-color"));
        }
    }

    @Test
    @DisplayName("the warning has no stack trace and does not contain the configured value")
    void warningRevealsNoConfiguredValue() {
        try (LogEvents events = LogEvents.capture(AppearanceService.class)) {
            new AppearanceService(new AppearanceProperties(LEAK_MARK_COLOR, LEAK_MARK_FAMILY));

            assertThat(warnings(events)).hasSize(2).allSatisfy(event -> {
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getArgumentArray()).isNullOrEmpty();
                assertThat(rendered(event))
                        .doesNotContain(LEAK_MARK_COLOR)
                        .doesNotContain(LEAK_MARK_FAMILY)
                        .doesNotContain("appearance-leak-check");
            });
        }
    }

    @Test
    @DisplayName("current() returns the same instance every time and never warns again")
    void resolvesOnlyOnce() {
        try (LogEvents events = LogEvents.capture(AppearanceService.class)) {
            AppearanceService service = new AppearanceService(new AppearanceProperties("red", "mono"));
            ResolvedAppearance first = service.current();
            assertThat(warnings(events)).hasSize(2);

            for (int i = 0; i < 5; i++) {
                assertThat(service.current()).isSameAs(first);
            }
            assertThat(warnings(events)).hasSize(2);
        }
    }

    @Test
    @DisplayName("blank values take the defaults without a warning")
    void blankValuesDoNotWarn() {
        try (LogEvents events = LogEvents.capture(AppearanceService.class)) {
            AppearanceService service = new AppearanceService(new AppearanceProperties("  ", ""));

            assertThat(service.current()).isEqualTo(new ResolvedAppearance(BrandColor.BLUE, FontFamily.SANS));
            assertThat(warnings(events)).isEmpty();
        }
    }
}
