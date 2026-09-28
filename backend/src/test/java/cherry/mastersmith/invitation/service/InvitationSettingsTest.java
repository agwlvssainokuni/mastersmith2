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
package cherry.mastersmith.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.web.MastersmithWebProperties;
import cherry.mastersmith.invitation.domain.InvitationToken;
import cherry.mastersmith.invitation.domain.UnavailableReason;
import cherry.mastersmith.mail.service.MailSender;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

/** 招待の設定の起動時の確かめの単体テスト（BR1.3〜BR1.6、{@code observability-design.md} 2.2）。 */
class InvitationSettingsTest {

    private static final String BASE = "http://localhost:8080";

    private static InvitationSettings settings(Duration validity, Duration retention, String baseUrl, boolean smtp) {
        MailSender sender = mock(MailSender.class);
        when(sender.isConfigured()).thenReturn(smtp);
        return new InvitationSettings(
                new InvitationProperties(validity, retention, new InvitationProperties.Cleanup("-")),
                new MastersmithWebProperties(baseUrl, false, DataSize.ofMegabytes(1)),
                sender);
    }

    private static InvitationSettings settings(Duration validity) {
        return settings(validity, Duration.ofDays(90), BASE, true);
    }

    @Test
    @DisplayName("the validity hours are the decimal hours of the setting (1, 24 and 48 hours)")
    void validityHours() {
        assertThat(settings(Duration.ofHours(1)).validityHours()).isEqualTo("1");
        assertThat(settings(Duration.ofHours(24)).validityHours()).isEqualTo("24");
        assertThat(settings(Duration.ofHours(48)).validityHours()).isEqualTo("48");
        assertThat(settings(Duration.ofHours(48)).validity()).isEqualTo(Duration.ofHours(48));
        assertThat(settings(Duration.ofHours(24)).retention()).isEqualTo(Duration.ofDays(90));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT-1H", "PT90M", "PT30M"})
    @DisplayName("a validity that is not a positive whole number of hours stops the startup without its value")
    void invalidValidity(String value) {
        assertThatThrownBy(() -> settings(Duration.parse(value)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mastersmith.invitation.validity")
                .hasMessageNotContaining(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-P1D", "PT36H", "PT1H"})
    @DisplayName("a retention that is not a positive whole number of days stops the startup")
    void invalidRetention(String value) {
        assertThatThrownBy(() -> settings(Duration.ofHours(24), Duration.parse(value), BASE, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mastersmith.invitation.retention");
    }

    @Test
    @DisplayName("usable base URL and SMTP make invitations enabled, and the URL is built from the base URL")
    void enabled() {
        InvitationSettings settings = settings(Duration.ofHours(24), Duration.ofDays(90), BASE + "/", true);
        InvitationToken token = InvitationToken.generate(new SecureRandom());

        assertThat(settings.availability().enabled()).isTrue();
        assertThat(settings.registrationUrl(token).value()).isEqualTo(BASE + "/register#token=" + token.value());
    }

    @Test
    @DisplayName("the reasons are listed for a missing base URL, missing SMTP and both, and no URL is built")
    void reasons() {
        assertThat(settings(Duration.ofHours(24), Duration.ofDays(90), null, true)
                        .availability()
                        .unavailableReasons())
                .containsExactly(UnavailableReason.BASE_URL_NOT_CONFIGURED);
        assertThat(settings(Duration.ofHours(24), Duration.ofDays(90), BASE, false)
                        .availability()
                        .unavailableReasons())
                .containsExactly(UnavailableReason.SMTP_NOT_CONFIGURED);
        InvitationSettings none = settings(Duration.ofHours(24), Duration.ofDays(90), "", false);
        assertThat(none.availability().unavailableReasons())
                .containsExactly(UnavailableReason.BASE_URL_NOT_CONFIGURED, UnavailableReason.SMTP_NOT_CONFIGURED);
        assertThatThrownBy(() -> none.registrationUrl(InvitationToken.generate(new SecureRandom())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a malformed base URL gives one WARN with the item name only, and the INFO shows the availability")
    void logs() {
        try (LogEvents events = LogEvents.capture(InvitationSettings.class)) {
            settings(Duration.ofHours(24), Duration.ofDays(90), "ftp://secret-host.example.com", true);

            List<ILoggingEvent> warns = events.list().stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .toList();
            assertThat(warns).singleElement().satisfies(event -> {
                assertThat(event.getKeyValuePairs())
                        .singleElement()
                        .satisfies(pair -> assertThat(pair.value).isEqualTo("mastersmith.web.base-url"));
                assertThat(event.getFormattedMessage()).doesNotContain("secret-host");
            });
            List<ILoggingEvent> infos = events.list().stream()
                    .filter(event -> event.getLevel() == Level.INFO)
                    .toList();
            assertThat(infos)
                    .singleElement()
                    .satisfies(event -> assertThat(event.getKeyValuePairs().toString())
                            .contains("enabled=\"false\"")
                            .contains("BASE_URL_NOT_CONFIGURED")
                            .doesNotContain("secret-host"));
        }
    }

    @Test
    @DisplayName("a missing base URL gives no WARN")
    void missingGivesNoWarn() {
        try (LogEvents events = LogEvents.capture(InvitationSettings.class)) {
            settings(Duration.ofHours(24), Duration.ofDays(90), null, true);

            assertThat(events.list()).noneMatch(event -> event.getLevel() == Level.WARN);
        }
    }
}
