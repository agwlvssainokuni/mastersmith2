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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cherry.mastersmith.common.web.MastersmithWebProperties;
import cherry.mastersmith.invitation.domain.Invitation;
import cherry.mastersmith.invitation.domain.InvitationCancelledEvent;
import cherry.mastersmith.invitation.domain.InvitationEmail;
import cherry.mastersmith.invitation.domain.InvitationIssuedEvent;
import cherry.mastersmith.invitation.domain.InvitationResentEvent;
import cherry.mastersmith.invitation.domain.InvitationState;
import cherry.mastersmith.invitation.domain.SendResult;
import cherry.mastersmith.invitation.domain.UnavailableReason;
import cherry.mastersmith.invitation.repository.InvitationRepository;
import cherry.mastersmith.mail.domain.MailFailureKind;
import cherry.mastersmith.mail.domain.MailSendResult;
import cherry.mastersmith.mail.service.MailSender;
import cherry.mastersmith.user.domain.FieldError;
import cherry.mastersmith.user.domain.FieldErrorReason;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.RedactedText;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.service.UserAccountService;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.util.unit.DataSize;

/**
 * 招待の管理の業務処理の単体テスト（判定の順、呼ぶ・呼ばない、順、結果の型。{@code unit-test-instructions.md} 5節）。DB アクセス・
 * 利用者の確かめ・送信の入口・出来事の知らせを差し替え、トランザクションは渡した処理をそのまま行う差し替えにする。
 */
class InvitationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.1", "agent", "trace");

    private static final long ADMIN = 9;

    private final InvitationRepository repository = mock(InvitationRepository.class);

    private final UserAccountService users = mock(UserAccountService.class);

    private final InvitationMailDispatcher dispatcher = mock(InvitationMailDispatcher.class);

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private final AtomicLong ids = new AtomicLong(100);

    private InvitationService service;

    private InvitationService service(String baseUrl, boolean smtp) {
        MailSender sender = mock(MailSender.class);
        when(sender.isConfigured()).thenReturn(smtp);
        InvitationSettings settings = new InvitationSettings(
                new InvitationProperties(
                        Duration.ofHours(24), Duration.ofDays(90), new InvitationProperties.Cleanup("-")),
                new MastersmithWebProperties(baseUrl, false, DataSize.ofMegabytes(1)),
                sender);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new InvitationService(
                repository,
                users,
                dispatcher,
                new InvitationTokenIssuer(),
                settings,
                new NoOpInvitationBarrier(),
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                manager);
    }

    @BeforeEach
    void setUp() {
        service = service("http://localhost:8080", true);
        when(repository.saveAndFlush(any(Invitation.class))).thenAnswer(invocation -> {
            Invitation invitation = invocation.getArgument(0);
            if (invitation.getInvitationId() == null) {
                ReflectionTestUtils.setField(invitation, "invitationId", ids.incrementAndGet());
            }
            return invitation;
        });
        when(users.findDisplayName(ADMIN)).thenReturn(Optional.of(new RedactedText("管理者")));
        when(dispatcher.dispatch(anyLong(), any(), any(), any(), any())).thenReturn(MailSendResult.sent());
    }

    private static Invitation invitation(long id, Instant invitedAt, Duration validity) {
        Invitation invitation = new Invitation(
                new InvitationEmail("hanako@example.com"),
                Language.JA,
                new byte[32],
                ADMIN,
                invitedAt,
                invitedAt.plus(validity));
        ReflectionTestUtils.setField(invitation, "invitationId", id);
        return invitation;
    }

    private InviteResult invite(String email) {
        return service.invite(ADMIN, ORIGIN, new InviteCommand(email, "ja"));
    }

    @Test
    @DisplayName("invalid input is refused first without the database or sending, even when not configured")
    void invalidFirst() {
        InvitationService unconfigured = service(null, false);

        InviteResult result = unconfigured.invite(ADMIN, ORIGIN, new InviteCommand("bad", "JA"));

        assertThat(result)
                .isEqualTo(new InviteResult.Invalid(List.of(
                        new FieldError("email", FieldErrorReason.INVALID_VALUE),
                        new FieldError("language", FieldErrorReason.INVALID_VALUE))));
        verifyNoInteractions(repository, users, dispatcher, publisher);
    }

    @Test
    @DisplayName("an unusable configuration is refused before the database and sending")
    void notConfigured() {
        InvitationService unconfigured = service(null, false);

        assertThat(unconfigured.invite(ADMIN, ORIGIN, new InviteCommand("hanako@example.com", "ja")))
                .isEqualTo(new InviteResult.NotConfigured(
                        List.of(UnavailableReason.BASE_URL_NOT_CONFIGURED, UnavailableReason.SMTP_NOT_CONFIGURED)));
        assertThat(unconfigured.resend(ADMIN, ORIGIN, 1))
                .isEqualTo(new ResendResult.NotConfigured(
                        List.of(UnavailableReason.BASE_URL_NOT_CONFIGURED, UnavailableReason.SMTP_NOT_CONFIGURED)));
        verifyNoInteractions(repository, users, dispatcher, publisher);
    }

    @Test
    @DisplayName("a registered email is refused before looking at pending invitations")
    void registered() {
        when(users.existsByEmail(new RedactedText("hanako@example.com"))).thenReturn(true);

        assertThat(invite(" Hanako@Example.com")).isEqualTo(new InviteResult.EmailRegistered());
        verify(repository, never()).findPendingByEmailForUpdate(any());
        verifyNoInteractions(dispatcher, publisher);
    }

    @Test
    @DisplayName("a valid pending invitation is refused with its id and the page from its list position")
    void alreadyPending() {
        Invitation pending = invitation(7, NOW.minusSeconds(60), Duration.ofHours(24));
        when(repository.findPendingByEmailForUpdate(any())).thenReturn(Optional.of(pending));
        when(repository.countBefore(InvitationState.PENDING, pending.getInvitedAt(), 7))
                .thenReturn(20L);

        assertThat(invite("hanako@example.com")).isEqualTo(new InviteResult.AlreadyPending(7, 2));
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(dispatcher, publisher);
    }

    @Test
    @DisplayName("an expired pending invitation is replaced, then a new one is created, sent and recorded in order")
    void replacesExpired() {
        Invitation expired = invitation(7, NOW.minus(Duration.ofHours(25)), Duration.ofHours(24));
        when(repository.findPendingByEmailForUpdate(any())).thenReturn(Optional.of(expired));
        when(dispatcher.dispatch(anyLong(), any(), any(), any(), any()))
                .thenReturn(MailSendResult.failed(MailFailureKind.REJECTED));

        InviteResult result = invite("hanako@example.com");

        assertThat(expired.getState()).isEqualTo(InvitationState.REPLACED);
        assertThat(expired.getEndedAt()).isEqualTo(NOW);
        assertThat(result).isInstanceOfSatisfying(InviteResult.Invited.class, invited -> {
            assertThat(invited.summary().invitationId()).isEqualTo(101);
            assertThat(invited.summary().sendResult()).isEqualTo("FAILED");
            assertThat(invited.summary().invitedBy()).isEqualTo("管理者");
            assertThat(invited.summary().expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
            assertThat(invited.summary().expired()).isFalse();
        });
        InOrder order = inOrder(repository, publisher, dispatcher);
        order.verify(repository).saveAndFlush(expired);
        order.verify(repository).saveAndFlush(any(Invitation.class));
        order.verify(publisher).publishEvent(any(InvitationIssuedEvent.class));
        order.verify(dispatcher).dispatch(eq(101L), eq(InvitationOperation.INVITE), any(), eq(Language.JA), any());
        order.verify(repository).recordSendResult(eq(101L), any(), eq(SendResult.FAILED));
    }

    @Test
    @DisplayName("a concurrent unique violation returns the winner, retries once when the winner is gone, then fails")
    void uniqueViolation() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException(
                "dup",
                new ConstraintViolationException(
                        "dup", new SQLException("dup"), "PUBLIC.UK_INVITATIONS_PENDING_EMAIL_INDEX_2"));
        when(repository.findPendingByEmailForUpdate(any())).thenReturn(Optional.empty());
        Invitation winner = invitation(55, NOW, Duration.ofHours(24));
        when(repository.saveAndFlush(any(Invitation.class))).thenThrow(violation);
        when(repository.findByEmailAndState(any(), eq(InvitationState.PENDING)))
                .thenReturn(Optional.of(winner))
                .thenReturn(Optional.empty());

        assertThat(invite("hanako@example.com")).isEqualTo(new InviteResult.AlreadyPending(55, 1));
        assertThatThrownBy(() -> invite("hanako@example.com")).isInstanceOf(IllegalStateException.class);
        verify(repository, times(3)).saveAndFlush(any(Invitation.class));
        verifyNoInteractions(dispatcher, publisher);
    }

    @Test
    @DisplayName("other integrity violations are passed through and the constraint detection is exact")
    void otherViolations() {
        when(repository.findPendingByEmailForUpdate(any())).thenReturn(Optional.empty());
        DataIntegrityViolationException other = new DataIntegrityViolationException(
                "other", new ConstraintViolationException("other", new SQLException("x"), "UK_INVITATIONS_TOKEN_HASH"));
        when(repository.saveAndFlush(any(Invitation.class))).thenThrow(other);

        assertThatThrownBy(() -> invite("hanako@example.com")).isSameAs(other);
        assertThat(InvitationService.isPendingEmailViolation(new DataIntegrityViolationException("plain")))
                .isFalse();
    }

    @Test
    @DisplayName(
            "the list maps PENDING to FAILED, marks expiry by the clock and shows an empty name for a missing admin")
    void listRows() {
        Invitation pending = invitation(1, NOW.minus(Duration.ofHours(30)), Duration.ofHours(24));
        Invitation other = new Invitation(
                new InvitationEmail("taro@example.com"), Language.EN, new byte[32], 77, NOW, NOW.plusSeconds(60));
        ReflectionTestUtils.setField(other, "invitationId", 2L);
        when(repository.countByState(InvitationState.PENDING)).thenReturn(2L);
        when(repository.findPage(eq(InvitationState.PENDING), any())).thenReturn(List.of(other, pending));
        when(users.findDisplayName(77)).thenReturn(Optional.empty());

        ListResult result = service.list(null);

        assertThat(result).isInstanceOfSatisfying(ListResult.Listed.class, listed -> {
            InvitationPage page = listed.page();
            assertThat(page.page()).isEqualTo(1);
            assertThat(page.size()).isEqualTo(20);
            assertThat(page.total()).isEqualTo(2);
            assertThat(page.invitationEnabled()).isTrue();
            assertThat(page.items())
                    .extracting(InvitationSummary::invitedBy, InvitationSummary::sendResult, InvitationSummary::expired)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("", "FAILED", false),
                            org.assertj.core.groups.Tuple.tuple("管理者", "FAILED", true));
            assertThat(page.toString()).doesNotContain("hanako").doesNotContain("管理者");
        });
        assertThat(service.list("0")).isEqualTo(new ListResult.InvalidPage());
        assertThat(service.list("3"))
                .isInstanceOfSatisfying(
                        ListResult.Listed.class,
                        listed -> assertThat(listed.page().items()).isEmpty());
    }

    @Test
    @DisplayName("resend and cancel are NotFound for a missing or ended invitation and change nothing")
    void notFound() {
        Invitation ended = invitation(3, NOW, Duration.ofHours(24));
        ended.cancel(NOW);
        when(repository.findByIdForUpdate(1)).thenReturn(Optional.empty());
        when(repository.findByIdForUpdate(3)).thenReturn(Optional.of(ended));

        assertThat(service.resend(ADMIN, ORIGIN, 1)).isEqualTo(new ResendResult.NotFound());
        assertThat(service.resend(ADMIN, ORIGIN, 3)).isEqualTo(new ResendResult.NotFound());
        assertThat(service.cancel(ADMIN, ORIGIN, 1)).isEqualTo(new CancelResult.NotFound());
        assertThat(service.cancel(ADMIN, ORIGIN, 3)).isEqualTo(new CancelResult.NotFound());
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(dispatcher, publisher);
    }

    @Test
    @DisplayName("resend replaces the token and expiry only, sends and records; cancel ends the invitation")
    void resendAndCancel() {
        Instant invitedAt = NOW.minus(Duration.ofHours(30));
        Invitation pending = invitation(4, invitedAt, Duration.ofHours(24));
        byte[] before = pending.getTokenHash();
        when(repository.findByIdForUpdate(4)).thenReturn(Optional.of(pending));

        ResendResult resent = service.resend(ADMIN, ORIGIN, 4);

        assertThat(resent).isInstanceOfSatisfying(ResendResult.Resent.class, r -> {
            assertThat(r.summary().invitedAt()).isEqualTo(invitedAt);
            assertThat(r.summary().expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
            assertThat(r.summary().sendResult()).isEqualTo("SENT");
        });
        assertThat(pending.getTokenHash()).isNotEqualTo(before);
        verify(publisher).publishEvent(any(InvitationResentEvent.class));
        verify(dispatcher).dispatch(eq(4L), eq(InvitationOperation.RESEND), any(), any(), any());
        verify(repository).recordSendResult(eq(4L), any(), eq(SendResult.SENT));

        assertThat(service.cancel(ADMIN, ORIGIN, 4)).isEqualTo(new CancelResult.Cancelled());
        assertThat(pending.getState()).isEqualTo(InvitationState.CANCELLED);
        verify(publisher).publishEvent(any(InvitationCancelledEvent.class));
    }
}
