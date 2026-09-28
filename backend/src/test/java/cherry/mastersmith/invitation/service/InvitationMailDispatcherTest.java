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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.web.MastersmithWebProperties;
import cherry.mastersmith.invitation.domain.InvitationEmail;
import cherry.mastersmith.invitation.domain.InvitationToken;
import cherry.mastersmith.invitation.domain.RegistrationUrl;
import cherry.mastersmith.mail.domain.MailFailureKind;
import cherry.mastersmith.mail.domain.MailRequest;
import cherry.mastersmith.mail.domain.MailSendResult;
import cherry.mastersmith.mail.domain.MailUnexpectedException;
import cherry.mastersmith.mail.service.MailSender;
import cherry.mastersmith.user.domain.Language;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;

/** 招待メールの送信の入口の単体テスト（BR4.1・BR4.2、NFR5.1・NFR2.3、{@code performance-design.md} 2.1）。 */
class InvitationMailDispatcherTest {

    private static final String EMAIL = "leak-check-invitee@example.com";

    private MailSender sender;

    private InvitationMailDispatcher dispatcher;

    private RegistrationUrl url;

    @BeforeEach
    void setUp() {
        sender = mock(MailSender.class);
        when(sender.isConfigured()).thenReturn(true);
        InvitationSettings settings = new InvitationSettings(
                new InvitationProperties(
                        Duration.ofHours(48), Duration.ofDays(90), new InvitationProperties.Cleanup("-")),
                new MastersmithWebProperties("http://localhost:8080", false, DataSize.ofMegabytes(1)),
                sender);
        dispatcher = new InvitationMailDispatcher(sender, settings);
        url = settings.registrationUrl(InvitationToken.generate(new SecureRandom()));
    }

    private MailSendResult dispatch() {
        return dispatcher.dispatch(7, InvitationOperation.RESEND, new InvitationEmail(EMAIL), Language.EN, url);
    }

    @Test
    @DisplayName("the request has the invitation template, the invitation language, the recipient and two variables")
    void request() {
        when(sender.send(any())).thenReturn(MailSendResult.sent());

        assertThat(dispatch().isSent()).isTrue();

        ArgumentCaptor<MailRequest> captor = ArgumentCaptor.forClass(MailRequest.class);
        verify(sender).send(captor.capture());
        MailRequest request = captor.getValue();
        assertThat(request.templateId()).isEqualTo("invitation");
        assertThat(request.language()).isEqualTo("en");
        assertThat(request.to()).isEqualTo(EMAIL);
        assertThat(request.variables()).isEqualTo(Map.of("registrationUrl", url.value(), "validityHours", "48"));
    }

    @Test
    @DisplayName("a success writes no log")
    void successNoLog() {
        when(sender.send(any())).thenReturn(MailSendResult.sent());

        try (LogEvents events = LogEvents.capture(InvitationMailDispatcher.class)) {
            dispatch();
            assertThat(events.list()).isEmpty();
        }
    }

    @Test
    @DisplayName("a failure writes one INFO with the invitation id, operation and failure kind only, and no WARN")
    void failureLog() {
        when(sender.send(any())).thenReturn(MailSendResult.failed(MailFailureKind.TIMEOUT));

        try (LogEvents events = LogEvents.capture(InvitationMailDispatcher.class)) {
            assertThat(dispatch().failureKind()).isEqualTo(MailFailureKind.TIMEOUT);

            assertThat(events.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getKeyValuePairs())
                        .extracting(pair -> pair.key + "=" + pair.value)
                        .containsExactly("invitationId=7", "operation=RESEND", "failureKind=TIMEOUT");
                assertThat(event.getFormattedMessage()).doesNotContain(EMAIL).doesNotContain("token");
            });
        }
    }

    @Test
    @DisplayName("calling inside a transaction stops before sending")
    void insideTransaction() {
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(
                new DriverManagerDataSource("jdbc:h2:mem:dispatcher;DB_CLOSE_DELAY=-1", "sa", "")));

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> dispatch()))
                .isInstanceOf(IllegalStateException.class);
        verify(sender, never()).send(any());
    }

    @Test
    @DisplayName("an unexpected mail failure is passed through")
    void unexpectedPassesThrough() {
        when(sender.send(any())).thenThrow(MailUnexpectedException.of(new IllegalStateException("テスト用の想定外")));

        assertThatThrownBy(this::dispatch).isInstanceOf(MailUnexpectedException.class);
    }
}
