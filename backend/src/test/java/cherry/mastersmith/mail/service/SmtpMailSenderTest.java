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
package cherry.mastersmith.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.mail.config.EncryptionMode;
import cherry.mastersmith.mail.config.MailSettings;
import cherry.mastersmith.mail.domain.MailFailureKind;
import cherry.mastersmith.mail.domain.MailRequest;
import cherry.mastersmith.mail.domain.MailSendResult;
import cherry.mastersmith.mail.domain.MailUnexpectedException;
import cherry.mastersmith.mail.template.MailTemplateRegistry;
import cherry.mastersmith.mail.template.RenderedMail;
import cherry.mastersmith.mail.testsupport.MailTestTemplates;
import cherry.mastersmith.mail.transport.SendAttempt;
import cherry.mastersmith.mail.transport.SmtpMailTransport;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import jakarta.mail.internet.InternetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * 入口の流れ・ログ・Observation の単体テスト（契約 C1、BR1.7・BR6.1〜BR6.3、NFR 設計の Q2 B）。
 *
 * <p>送信の部品（{@link SmtpMailTransport}）だけを Mockito で差し替える。実際の SMTP での送信は結合テスト（MailSendIT ほか）で
 * 確かめる。Observation は、新しいテストの依存を足さず、文脈を集める小さな受け手で確かめる。
 */
class SmtpMailSenderTest {

    private static final String TO = "taro@example.com";

    private static final String SECRET_LINK = "https://example.com/register?t=secret-token-value";

    /** 止まった Observation の文脈を集める受け手。 */
    static final class CollectingHandler implements ObservationHandler<Observation.Context> {

        final List<Observation.Context> stopped = new ArrayList<>();

        @Override
        public void onStop(Observation.Context context) {
            stopped.add(context);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }

    private final MailTemplateRegistry registry = MailTestTemplates.registry();

    private final SmtpMailTransport transport = mock(SmtpMailTransport.class);

    private final CollectingHandler handler = new CollectingHandler();

    private ObservationRegistry observationRegistry;

    private MailSettings.Usable usable;

    @BeforeEach
    void setUp() throws Exception {
        observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(handler);
        usable = new MailSettings.Usable(
                new JavaMailSenderImpl(),
                new InternetAddress("noreply@example.com", "MasterSmith", "UTF-8"),
                EncryptionMode.NONE);
    }

    private SmtpMailSender sender(MailSettings settings) {
        return new SmtpMailSender(settings, registry, transport, observationRegistry);
    }

    private static MailRequest request(String templateId, String language, String to) {
        return new MailRequest(templateId, language, to, Map.of("name", "山田<b>", "link", SECRET_LINK));
    }

    private Map<String, String> tags() {
        assertThat(handler.stopped).hasSize(1);
        return handler.stopped.getFirst().getAllKeyValues().stream()
                .collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue));
    }

    private static String text(ILoggingEvent event) {
        return event.getFormattedMessage() + event.getKeyValuePairs();
    }

    @Test
    @DisplayName("without a usable setting nothing is validated, rendered or sent and NOT_CONFIGURED is returned")
    void notConfigured() {
        SmtpMailSender mailSender = sender(new MailSettings.NotConfigured());

        MailSendResult result = mailSender.send(request("sample", "ja", "not an address"));

        assertThat(mailSender.isConfigured()).isFalse();
        assertThat(result).isEqualTo(MailSendResult.failed(MailFailureKind.NOT_CONFIGURED));
        verify(transport, never()).send(any(), any(), anyString());
        assertThat(sender(new MailSettings.Invalid(List.of("x")))
                        .send(request("sample", "ja", TO))
                        .failureKind())
                .isEqualTo(MailFailureKind.NOT_CONFIGURED);
        assertThat(sender(usable).isConfigured()).isTrue();
    }

    @Test
    @DisplayName("a validation failure neither renders nor sends")
    void validationFailure() {
        assertThat(sender(usable)
                        .send(request("sample", "ja", "Taro@Example.com"))
                        .failureKind())
                .isEqualTo(MailFailureKind.INVALID_INPUT);
        assertThat(sender(usable).send(request("nothing", "ja", TO)).failureKind())
                .isEqualTo(MailFailureKind.TEMPLATE_ERROR);
        verify(transport, never()).send(any(), any(), anyString());
    }

    @Test
    @DisplayName("a template that renders without a valid title or lang is TEMPLATE_ERROR and is not sent")
    void templateErrorIsNotSent() {
        MailTemplateRegistry broken = mock(MailTemplateRegistry.class);
        when(broken.contains("sample")).thenReturn(true);
        when(broken.variableNames("sample")).thenReturn(java.util.Optional.of(java.util.Set.of("name", "link")));
        when(broken.render(any(), any(), any()))
                .thenReturn(java.util.Optional.of("<html lang=\"en\"><title>x</title></html>"));

        MailSendResult result =
                new SmtpMailSender(usable, broken, transport, observationRegistry).send(request("sample", "ja", TO));

        assertThat(result.failureKind()).isEqualTo(MailFailureKind.TEMPLATE_ERROR);
        verify(transport, never()).send(any(), any(), anyString());
    }

    @Test
    @DisplayName("the rendered mail is handed to the transport and its result is returned as is, with one INFO log")
    void sentResultIsReturned() {
        ArgumentCaptor<RenderedMail> mail = ArgumentCaptor.forClass(RenderedMail.class);
        when(transport.send(any(), mail.capture(), anyString()))
                .thenReturn(new SendAttempt(MailSendResult.sent(), null));

        try (LogEvents events = LogEvents.capture(SmtpMailSender.class)) {
            MailSendResult result = sender(usable).send(request("sample", "en", TO));

            assertThat(result.isSent()).isTrue();
            assertThat(mail.getValue().subject()).isEqualTo("Notice for 山田<b>");
            assertThat(mail.getValue().htmlBody()).contains("山田&lt;b&gt;");
            assertThat(events.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getKeyValuePairs())
                        .extracting(pair -> pair.key)
                        .containsExactly("templateId", "language");
                assertThat(text(event))
                        .doesNotContain(TO)
                        .doesNotContain(SECRET_LINK)
                        .doesNotContain("山田");
            });
        }
        assertThat(tags())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "mail.template", "sample",
                        "mail.language", "en",
                        "mail.outcome", "sent",
                        "mail.failure.kind", "none"));
        assertThat(handler.stopped.getFirst().getName()).isEqualTo(SmtpMailSender.OBSERVATION_NAME);
        assertThat(handler.stopped.getFirst().getError()).isNull();
    }

    @Test
    @DisplayName("a failure logs one WARN with only the four keys and the observation is not an error")
    void failureLogsOneWarning() {
        when(transport.send(any(), any(), anyString()))
                .thenReturn(new SendAttempt(
                        MailSendResult.failed(MailFailureKind.REJECTED), "org.springframework.mail.MailSendException"));

        try (LogEvents events = LogEvents.capture(SmtpMailSender.class)) {
            assertThat(sender(usable).send(request("sample", "ja", TO)).failureKind())
                    .isEqualTo(MailFailureKind.REJECTED);

            assertThat(events.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getKeyValuePairs())
                        .extracting(pair -> pair.key)
                        .containsExactly("templateId", "language", "failureKind", "exceptionType");
                assertThat(text(event)).doesNotContain(TO).doesNotContain(SECRET_LINK);
            });
        }
        assertThat(tags()).containsEntry("mail.outcome", "failed").containsEntry("mail.failure.kind", "rejected");
        assertThat(handler.stopped.getFirst().getError()).isNull();
    }

    @Test
    @DisplayName("an unknown template id or language is logged and tagged as unknown")
    void unknownValuesAreUnknown() {
        try (LogEvents events = LogEvents.capture(SmtpMailSender.class)) {
            sender(usable).send(request("evil\r\nX: 1", "fr\n", TO));

            assertThat(events.list())
                    .singleElement()
                    .satisfies(event -> assertThat(text(event))
                            .contains("templateId=\"unknown\"")
                            .contains("language=\"unknown\"")
                            .doesNotContain("evil"));
        }
        assertThat(tags()).containsEntry("mail.template", "unknown").containsEntry("mail.language", "unknown");
        assertThat(tags().values())
                .allSatisfy(value -> assertThat(value).doesNotContain(TO).doesNotContain(SECRET_LINK));
    }

    @Test
    @DisplayName("an unexpected failure is rethrown, logged nowhere by U1 and recorded as the observation error")
    void unexpectedFailure() {
        MailUnexpectedException unexpected = MailUnexpectedException.of(new IllegalStateException(TO));
        when(transport.send(any(), any(), anyString())).thenThrow(unexpected);

        try (LogEvents events = LogEvents.capture(SmtpMailSender.class)) {
            assertThatThrownBy(() -> sender(usable).send(request("sample", "ja", TO)))
                    .isSameAs(unexpected);
            assertThat(events.list()).isEmpty();
        }
        assertThat(handler.stopped.getFirst().getError()).isSameAs(unexpected);
        assertThat(tags()).containsEntry("mail.failure.kind", "unexpected");
    }

    @Test
    @DisplayName("an unwrapped runtime exception inside U1 is wrapped without its message")
    void lastGuardWrapsRuntimeExceptions() {
        MailTemplateRegistry broken = mock(MailTemplateRegistry.class);
        when(broken.contains(any())).thenReturn(true);
        when(broken.variableNames(any())).thenThrow(new IllegalStateException("bug with " + TO));

        assertThatThrownBy(() -> new SmtpMailSender(usable, broken, transport, observationRegistry)
                        .send(request("sample", "ja", TO)))
                .isInstanceOfSatisfying(MailUnexpectedException.class, e -> {
                    assertThat(e.getMessage()).doesNotContain(TO);
                    assertThat(e.getCause()).isNull();
                });
    }
}
