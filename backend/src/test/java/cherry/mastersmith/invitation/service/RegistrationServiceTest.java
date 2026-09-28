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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cherry.mastersmith.invitation.domain.Invitation;
import cherry.mastersmith.invitation.domain.InvitationEmail;
import cherry.mastersmith.invitation.domain.InvitationToken;
import cherry.mastersmith.invitation.domain.LinkRejection;
import cherry.mastersmith.invitation.domain.RegistrationCompletedEvent;
import cherry.mastersmith.invitation.domain.RegistrationFailedEvent;
import cherry.mastersmith.invitation.repository.InvitationRepository;
import cherry.mastersmith.user.domain.FieldError;
import cherry.mastersmith.user.domain.FieldErrorReason;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.RedactedText;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.domain.Theme;
import cherry.mastersmith.user.service.CreateUserResult;
import cherry.mastersmith.user.service.NewUser;
import cherry.mastersmith.user.service.UserAccountService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** リンクの確かめと登録の完了の業務処理の単体テスト（BR3.2・BR7.1〜BR7.6・BR8.2・BR8.3）。 */
class RegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.1", "agent", "trace");

    private static final String PASSWORD = "テスト用パスワード-0000";

    private final InvitationRepository repository = mock(InvitationRepository.class);

    private final UserAccountService users = mock(UserAccountService.class);

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);

    private final InvitationToken token = InvitationToken.generate(new SecureRandom());

    private RegistrationService service;

    @BeforeEach
    void setUp() {
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        service = new RegistrationService(
                repository, users, new NoOpInvitationBarrier(), publisher, Clock.fixed(NOW, ZoneOffset.UTC), manager);
    }

    private Invitation invitation(Duration remaining) {
        Invitation invitation = new Invitation(
                new InvitationEmail("hanako@example.com"),
                Language.EN,
                token.hash(),
                9,
                NOW.minus(Duration.ofHours(1)),
                NOW.plus(remaining));
        ReflectionTestUtils.setField(invitation, "invitationId", 5L);
        return invitation;
    }

    private RegistrationCommand command(String tokenValue) {
        return new RegistrationCommand(tokenValue, "山田 花子", PASSWORD, PASSWORD, "ja", "dark", "lg");
    }

    private RegistrationFailedEvent failedEvent() {
        ArgumentCaptor<RegistrationFailedEvent> captor = ArgumentCaptor.forClass(RegistrationFailedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private boolean committedAsRollbackOnly() {
        ArgumentCaptor<TransactionStatus> captor = ArgumentCaptor.forClass(TransactionStatus.class);
        verify(manager).commit(captor.capture());
        return captor.getValue().isRollbackOnly();
    }

    @Test
    @DisplayName("invalid input is refused without reading the token and is not audited")
    void invalidInput() {
        CompleteResult result = service.complete(
                ORIGIN, new RegistrationCommand(token.value(), "", "short", "other", "ja", "dark", "lg"));

        assertThat(result)
                .isEqualTo(new CompleteResult.Invalid(List.of(
                        new FieldError("displayName", FieldErrorReason.REQUIRED),
                        new FieldError("password", FieldErrorReason.TOO_SHORT),
                        new FieldError("passwordConfirmation", FieldErrorReason.MISMATCH))));
        verifyNoInteractions(repository, users, publisher, manager);
    }

    @Test
    @DisplayName("a malformed token is refused without a transaction and audited as not found")
    void malformedToken() {
        assertThat(service.complete(ORIGIN, command("short"))).isEqualTo(new CompleteResult.Rejected());

        verifyNoInteractions(repository, users, manager);
        assertThat(failedEvent().reason()).isEqualTo(LinkRejection.INVITATION_NOT_FOUND);
        assertThat(failedEvent().invitationId()).isNull();
    }

    @Test
    @DisplayName("an unknown token is rolled back by its own mark and audited as not found")
    void unknownToken() {
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.empty());

        assertThat(service.complete(ORIGIN, command(token.value()))).isEqualTo(new CompleteResult.Rejected());
        assertThat(committedAsRollbackOnly()).isTrue();
        assertThat(failedEvent().reason()).isEqualTo(LinkRejection.INVITATION_NOT_FOUND);
    }

    @Test
    @DisplayName("an expired invitation is refused with its id before creating a user")
    void expired() {
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(invitation(Duration.ZERO)));

        assertThat(service.complete(ORIGIN, command(token.value()))).isEqualTo(new CompleteResult.Rejected());
        verify(users, never()).createUser(any());
        assertThat(committedAsRollbackOnly()).isTrue();
        RegistrationFailedEvent event = failedEvent();
        assertThat(event.reason()).isEqualTo(LinkRejection.INVITATION_EXPIRED);
        assertThat(event.invitationId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("a locked row whose hash changed (resent while waiting) is treated as not found")
    void hashChangedWhileWaiting() {
        Invitation invitation = invitation(Duration.ofHours(1));
        invitation.resend(InvitationToken.generate(new SecureRandom()).hash(), NOW, NOW.plus(Duration.ofHours(24)));
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(invitation));

        assertThat(service.complete(ORIGIN, command(token.value()))).isEqualTo(new CompleteResult.Rejected());
        assertThat(failedEvent().reason()).isEqualTo(LinkRejection.INVITATION_NOT_FOUND);
    }

    @Test
    @DisplayName("a valid invitation creates a non-admin user with the invitation email and completes the invitation")
    void completes() {
        Invitation invitation = invitation(Duration.ofSeconds(1));
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(invitation));
        when(users.createUser(any())).thenReturn(new CreateUserResult.Created(31));

        assertThat(service.complete(ORIGIN, command(token.value()))).isEqualTo(new CompleteResult.Completed());

        ArgumentCaptor<NewUser> created = ArgumentCaptor.forClass(NewUser.class);
        verify(users).createUser(created.capture());
        assertThat(created.getValue().email()).isEqualTo("hanako@example.com");
        assertThat(created.getValue().admin()).isFalse();
        assertThat(created.getValue().language()).isEqualTo(Language.JA);
        assertThat(created.getValue().theme()).isEqualTo(Theme.DARK);
        assertThat(created.getValue().fontSize()).isEqualTo(FontSize.LG);
        assertThat(invitation.getCompletedUserId()).isEqualTo(31L);
        assertThat(committedAsRollbackOnly()).isFalse();
        verify(publisher).publishEvent(any(RegistrationCompletedEvent.class));
        verify(publisher, never()).publishEvent(any(RegistrationFailedEvent.class));
    }

    @Test
    @DisplayName("an email registered in the meantime is rolled back and audited as EMAIL_ALREADY_REGISTERED")
    void emailAlreadyUsed() {
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(invitation(Duration.ofHours(1))));
        when(users.createUser(any())).thenReturn(new CreateUserResult.EmailAlreadyUsed());

        assertThat(service.complete(ORIGIN, command(token.value()))).isEqualTo(new CompleteResult.Rejected());
        assertThat(committedAsRollbackOnly()).isTrue();
        assertThat(failedEvent().reason()).isEqualTo(LinkRejection.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("verify returns the email and language only for a valid invitation of an unregistered email")
    void verifyLink() {
        Invitation invitation = invitation(Duration.ofHours(1));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(invitation));

        assertThat(service.verify(new TokenCommand(token.value())))
                .isEqualTo(new VerifyResult.Valid(new InvitationView("hanako@example.com", "en")));
        when(users.existsByEmail(new RedactedText("hanako@example.com"))).thenReturn(true);
        assertThat(service.verify(new TokenCommand(token.value()))).isEqualTo(new VerifyResult.Rejected());
        assertThat(service.verify(new TokenCommand("bad"))).isEqualTo(new VerifyResult.Rejected());
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(invitation(Duration.ZERO)));
        assertThat(service.verify(new TokenCommand(token.value()))).isEqualTo(new VerifyResult.Rejected());
        verifyNoInteractions(publisher);
        assertThat(new TokenCommand(token.value()).toString()).doesNotContain(token.value());
        assertThat(command(token.value()).toString())
                .doesNotContain(token.value())
                .doesNotContain(PASSWORD);
    }
}
