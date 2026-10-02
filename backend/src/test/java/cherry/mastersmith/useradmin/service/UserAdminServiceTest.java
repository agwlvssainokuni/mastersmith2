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
package cherry.mastersmith.useradmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.auth.domain.LockView;
import cherry.mastersmith.auth.service.LockAdministrationService;
import cherry.mastersmith.auth.service.LoginFailureResetPreparation;
import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.user.domain.FieldError;
import cherry.mastersmith.user.domain.FieldErrorReason;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.domain.SearchText;
import cherry.mastersmith.user.service.AdminRowsLock;
import cherry.mastersmith.user.service.ProfileCommand;
import cherry.mastersmith.user.service.ProfileUpdateResult;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.service.UserAdminSlice;
import cherry.mastersmith.user.service.UserAdminSummary;
import cherry.mastersmith.user.service.UserRowLock;
import cherry.mastersmith.useradmin.domain.AdminOperation;
import cherry.mastersmith.useradmin.domain.RejectionReason;
import cherry.mastersmith.useradmin.domain.UserAdminAuditEvent;
import cherry.mastersmith.useradmin.domain.UserAdminAuditFailure;
import cherry.mastersmith.useradmin.testsupport.RecordingTransactionManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** 利用者の管理の業務処理（B3 の一覧と氏名・言語の変更、BR1.1〜BR1.9・BR5.1〜BR5.3。B4 の5つの操作）の単体テスト。 */
class UserAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

    private final UserAccountService userAccounts = mock(UserAccountService.class);

    private final LockAdministrationService locks = mock(LockAdministrationService.class);

    private final RefreshTokenRevocationService revocations = mock(RefreshTokenRevocationService.class);

    private final UserAdminBarrier barrier = mock(UserAdminBarrier.class);

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    private UserAdminService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        service = new UserAdminService(
                userAccounts,
                locks,
                revocations,
                barrier,
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager);
    }

    private static UserAdminSummary summary(long id) {
        return new UserAdminSummary(id, "user" + id + "@example.com", "利用者 " + id, Language.JA, false, false, NOW);
    }

    @Test
    @DisplayName("a page is read in one read-only transaction with lock views and the self flag")
    void listsPage() {
        when(userAccounts.findAdminPage(null, 20, 20))
                .thenReturn(new UserAdminSlice(List.of(summary(5), summary(6)), 22));
        LockView locked = new LockView(true, NOW.plusSeconds(60), true);
        when(locks.lockViewsOf(List.of(5L, 6L))).thenReturn(Map.of(5L, locked, 6L, LockView.NONE));

        UserAdminListResult result = service.list(6, "2", null);

        assertThat(result).isInstanceOf(UserAdminListResult.Listed.class);
        UserAdminPage page = ((UserAdminListResult.Listed) result).page();
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.total()).isEqualTo(22);
        assertThat(page.items())
                .containsExactly(
                        new UserAdminEntry(summary(5), locked, false),
                        new UserAdminEntry(summary(6), LockView.NONE, true));
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().isReadOnly()).isTrue();
    }

    @Test
    @DisplayName("an invalid page is rejected before reading anything")
    void invalidPage() {
        for (String page : new String[] {"0", "abc", "", "1234567890"}) {
            assertThat(service.list(1, page, null)).isEqualTo(new UserAdminListResult.InvalidPage());
        }
        verifyNoInteractions(userAccounts, locks, transactionManager);
    }

    @Test
    @DisplayName("a search longer than 254 code points is rejected with q and TOO_LONG")
    void searchTooLong() {
        UserAdminListResult result = service.list(1, null, new SearchText("a".repeat(255)));

        assertThat(result)
                .isEqualTo(new UserAdminListResult.Invalid(List.of(new FieldError("q", FieldErrorReason.TOO_LONG))));
        verifyNoInteractions(userAccounts, locks);
    }

    @Test
    @DisplayName("when both page and q are invalid the page error wins")
    void pageErrorWins() {
        assertThat(service.list(1, "0", new SearchText("a".repeat(255))))
                .isEqualTo(new UserAdminListResult.InvalidPage());
    }

    @Test
    @DisplayName("the search text is passed through unchanged, and a missing page means page 1")
    void passesSearch() {
        SearchText q = new SearchText("  ");
        when(userAccounts.findAdminPage(eq(q), anyLong(), anyInt())).thenReturn(new UserAdminSlice(List.of(), 0));
        when(locks.lockViewsOf(List.of())).thenReturn(Map.of());

        UserAdminListResult result = service.list(1, null, q);

        verify(userAccounts).findAdminPage(q, 0, 20);
        assertThat(((UserAdminListResult.Listed) result).page().items()).isEmpty();
        assertThat(((UserAdminListResult.Listed) result).page().page()).isEqualTo(1);
    }

    @Test
    @DisplayName("a user missing from the lock views is shown as not locked")
    void missingLockView() {
        when(userAccounts.findAdminPage(null, 0, 20)).thenReturn(new UserAdminSlice(List.of(summary(7)), 1));
        when(locks.lockViewsOf(List.of(7L))).thenReturn(Map.of());

        UserAdminPage page = ((UserAdminListResult.Listed) service.list(1, "1", null)).page();

        assertThat(page.items().getFirst().lock()).isEqualTo(LockView.NONE);
    }

    @Test
    @DisplayName("the profile change is passed in one read-write transaction and its result is returned as is")
    void updateProfile() {
        ProfileCommand command = new ProfileCommand("新しい 氏名", "en");
        when(userAccounts.updateProfile(7, command)).thenReturn(new ProfileUpdateResult.Updated());
        when(userAccounts.updateProfile(8, command)).thenReturn(new ProfileUpdateResult.NotFound());

        assertThat(service.updateProfile(7, command)).isEqualTo(new ProfileUpdateResult.Updated());
        assertThat(service.updateProfile(8, command)).isEqualTo(new ProfileUpdateResult.NotFound());
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager, times(2)).getTransaction(definition.capture());
        assertThat(definition.getAllValues())
                .allSatisfy(def -> assertThat(def.isReadOnly()).isFalse());
    }

    @Test
    @DisplayName("neither the list nor the profile change emits audit events")
    void noAuditEvents() {
        when(userAccounts.findAdminPage(null, 0, 20)).thenReturn(new UserAdminSlice(List.of(summary(7)), 1));
        when(locks.lockViewsOf(List.of(7L))).thenReturn(Map.of());
        when(userAccounts.updateProfile(eq(7L), any())).thenReturn(new ProfileUpdateResult.Updated());

        service.list(1, "1", null);
        service.updateProfile(7, new ProfileCommand("氏名", "ja"));

        verifyNoInteractions(publisher, barrier, revocations);
    }

    @Test
    @DisplayName("entries and pages hide emails and names in their strings")
    void stringsHideValues() {
        UserAdminSummary leak =
                new UserAdminSummary(3, "leak-check@example.com", "漏れ確認 花子", Language.JA, true, false, NOW);
        UserAdminPage page = new UserAdminPage(List.of(new UserAdminEntry(leak, LockView.NONE, true)), 1, 20, 1);

        assertThat(page.toString())
                .doesNotContain("leak-check")
                .doesNotContain("漏れ確認")
                .contains("self=true");
    }

    // --- B4: 5つの操作（FS の 2.2〜2.7、BR2.1〜BR2.6・BR3.5・BR4.1〜BR4.6・BR6.1、reliability-design.md 5.3 の2行目）

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.9", "Agent/9", "trace-9");

    private static final long ACTOR = 1L;

    private static final long TARGET = 50L;

    private final RecordingTransactionManager recording = new RecordingTransactionManager();

    private UserAdminService operations() {
        return new UserAdminService(
                userAccounts, locks, revocations, barrier, publisher, Clock.fixed(NOW, ZoneOffset.UTC), recording);
    }

    private static UserAdminSummary target(boolean admin, boolean suspended) {
        return new UserAdminSummary(TARGET, "target@example.com", "対象 太郎", Language.JA, admin, suspended, NOW);
    }

    private static UserAdminSummary actor(boolean admin, boolean suspended) {
        return new UserAdminSummary(ACTOR, "actor@example.com", "管理 花子", Language.JA, admin, suspended, NOW);
    }

    private void givenAdminRows(UserAdminSummary target, Long... activeAdmins) {
        when(userAccounts.lockAdminRowsInIdOrder(TARGET))
                .thenReturn(new AdminRowsLock.Locked(Optional.ofNullable(target), Set.of(activeAdmins)));
    }

    private OperationResult run(AdminOperation operation) {
        UserAdminService service = operations();
        return switch (operation) {
            case GRANT_ADMIN -> service.grantAdmin(ACTOR, ORIGIN, TARGET);
            case REVOKE_ADMIN -> service.revokeAdmin(ACTOR, ORIGIN, TARGET);
            case SUSPEND -> service.suspend(ACTOR, ORIGIN, TARGET);
            case RESUME -> service.resume(ACTOR, ORIGIN, TARGET);
            case RESET_LOGIN_FAILURES -> service.resetLoginFailures(ACTOR, ORIGIN, TARGET);
        };
    }

    private UserAdminAuditEvent publishedEvent() {
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOf(UserAdminAuditEvent.class);
        return (UserAdminAuditEvent) event.getValue();
    }

    private void assertCommittedWithoutMark() {
        assertThat(recording.rollbackOnlyMarks()).isZero();
        assertThat(recording.commits()).isEqualTo(1);
        assertThat(recording.rollbacks()).isZero();
        assertThat(recording.definitions())
                .singleElement()
                .satisfies(def -> assertThat(def.isReadOnly()).isFalse());
    }

    private void assertNoWrite() {
        verify(userAccounts, never()).setAdmin(anyLong(), anyBoolean());
        verify(userAccounts, never()).setSuspended(anyLong(), anyBoolean());
        verify(locks, never()).completeFailureReset(anyLong());
        verifyNoInteractions(revocations);
    }

    @ParameterizedTest
    @EnumSource(AdminOperation.class)
    @DisplayName("Busy from the lock port marks rollback-only once and then touches nothing else (R-02)")
    void busyMarksRollbackOnly(AdminOperation operation) {
        when(userAccounts.lockAdminRowsInIdOrder(TARGET)).thenReturn(new AdminRowsLock.Busy());
        when(userAccounts.lockUserRow(TARGET)).thenReturn(new UserRowLock.Busy());
        when(userAccounts.findAdminSummary(TARGET)).thenReturn(Optional.of(target(false, false)));
        when(locks.prepareFailureReset(TARGET)).thenReturn(new LoginFailureResetPreparation.Busy());

        OperationResult result = run(operation);

        assertThat(result).isEqualTo(new OperationResult.Busy());
        assertThat(recording.rollbackOnlyMarks()).isEqualTo(1);
        assertThat(recording.rollbacks()).isEqualTo(1);
        assertThat(recording.commits()).isZero();
        verifyNoInteractions(barrier, publisher, revocations);
        switch (operation) {
            case GRANT_ADMIN, REVOKE_ADMIN, SUSPEND -> verify(userAccounts).lockAdminRowsInIdOrder(TARGET);
            case RESUME -> verify(userAccounts).lockUserRow(TARGET);
            case RESET_LOGIN_FAILURES -> {
                verify(userAccounts).findAdminSummary(TARGET);
                verify(locks).prepareFailureReset(TARGET);
            }
        }
        verifyNoMoreInteractions(userAccounts, locks);
    }

    @Test
    @DisplayName("the admin-rows operations pass the waiting point after locking and before deciding")
    void barrierAfterLock() {
        givenAdminRows(target(false, false), ACTOR);

        OperationResult result = run(AdminOperation.GRANT_ADMIN);

        assertThat(result).isEqualTo(new OperationResult.Done());
        InOrder order = inOrder(userAccounts, barrier, publisher);
        order.verify(userAccounts).lockAdminRowsInIdOrder(TARGET);
        order.verify(barrier).beforeCount(AdminOperation.GRANT_ADMIN, TARGET);
        order.verify(userAccounts).setAdmin(TARGET, true);
        order.verify(publisher).publishEvent(any(UserAdminAuditEvent.class));
    }

    static Stream<Arguments> businessRejections() {
        return Stream.of(
                Arguments.of(AdminOperation.GRANT_ADMIN, null, RejectionReason.USER_NOT_FOUND),
                Arguments.of(AdminOperation.GRANT_ADMIN, target(true, false), RejectionReason.NO_CHANGE),
                Arguments.of(AdminOperation.REVOKE_ADMIN, target(true, true), RejectionReason.TARGET_SUSPENDED),
                Arguments.of(AdminOperation.REVOKE_ADMIN, target(false, false), RejectionReason.NO_CHANGE),
                Arguments.of(AdminOperation.SUSPEND, target(false, true), RejectionReason.NO_CHANGE),
                Arguments.of(AdminOperation.SUSPEND, null, RejectionReason.USER_NOT_FOUND));
    }

    @ParameterizedTest
    @MethodSource("businessRejections")
    @DisplayName("a business rejection writes nothing, commits and emits one failed event with the reason")
    void businessRejection(AdminOperation operation, UserAdminSummary target, RejectionReason reason) {
        givenAdminRows(target, ACTOR);

        OperationResult result = run(operation);

        assertThat(result).isEqualTo(new OperationResult.Rejected(reason));
        assertNoWrite();
        assertCommittedWithoutMark();
        UserAdminAuditEvent event = publishedEvent();
        assertThat(event.operation()).isEqualTo(operation);
        assertThat(event.succeeded()).isFalse();
        assertThat(event.failure()).isEqualTo(UserAdminAuditFailure.of(reason));
        assertThat(event.actorUserId()).isEqualTo(ACTOR);
        assertThat(event.targetUserId()).isEqualTo(TARGET);
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.sourceIp()).isEqualTo("192.0.2.9");
    }

    @Test
    @DisplayName("operating on oneself is SELF_OPERATION for the four operations that apply it")
    void selfOperation() {
        UserAdminSummary self = actor(true, false);
        when(userAccounts.lockAdminRowsInIdOrder(ACTOR))
                .thenReturn(new AdminRowsLock.Locked(Optional.of(self), Set.of(ACTOR, 2L)));
        when(userAccounts.lockUserRow(ACTOR)).thenReturn(new UserRowLock.Locked(Optional.of(self)));
        UserAdminService service = operations();

        assertThat(service.grantAdmin(ACTOR, ORIGIN, ACTOR))
                .isEqualTo(new OperationResult.Rejected(RejectionReason.SELF_OPERATION));
        assertThat(service.revokeAdmin(ACTOR, ORIGIN, ACTOR))
                .isEqualTo(new OperationResult.Rejected(RejectionReason.SELF_OPERATION));
        assertThat(service.suspend(ACTOR, ORIGIN, ACTOR))
                .isEqualTo(new OperationResult.Rejected(RejectionReason.SELF_OPERATION));
        assertThat(service.resume(ACTOR, ORIGIN, ACTOR))
                .isEqualTo(new OperationResult.Rejected(RejectionReason.SELF_OPERATION));
        assertNoWrite();
    }

    @Test
    @DisplayName("removing the only active admin is LAST_ACTIVE_ADMIN even if the actor is no longer active")
    void lastActiveAdmin() {
        givenAdminRows(target(true, false), TARGET);

        assertThat(run(AdminOperation.REVOKE_ADMIN))
                .isEqualTo(new OperationResult.Rejected(RejectionReason.LAST_ACTIVE_ADMIN));
        assertThat(publishedEvent().failure()).isEqualTo(UserAdminAuditFailure.LAST_ACTIVE_ADMIN);
        assertNoWrite();
    }

    @ParameterizedTest
    @EnumSource(
            value = AdminOperation.class,
            names = {"GRANT_ADMIN", "REVOKE_ADMIN", "SUSPEND"})
    @DisplayName("an actor missing from the locked active admins is OperatorNotAdmin with a NOT_ADMIN event")
    void operatorNotAdminForAdminRows(AdminOperation operation) {
        boolean targetAdmin = operation == AdminOperation.REVOKE_ADMIN;
        givenAdminRows(target(targetAdmin, false), 2L, TARGET);

        assertThat(run(operation)).isEqualTo(new OperationResult.OperatorNotAdmin());
        assertNoWrite();
        assertCommittedWithoutMark();
        assertThat(publishedEvent().failure()).isEqualTo(UserAdminAuditFailure.NOT_ADMIN);
    }

    @Test
    @DisplayName("resume and reset re-read the actor without a lock and refuse a suspended or non-admin actor")
    void operatorNotAdminForResumeAndReset() {
        when(userAccounts.lockUserRow(TARGET)).thenReturn(new UserRowLock.Locked(Optional.of(target(false, true))));
        when(userAccounts.findAdminSummary(TARGET)).thenReturn(Optional.of(target(false, false)));
        when(locks.prepareFailureReset(TARGET)).thenReturn(new LoginFailureResetPreparation.Ready());
        when(userAccounts.findAdminSummary(ACTOR))
                .thenReturn(Optional.of(actor(true, true)), Optional.of(actor(false, false)), Optional.empty());
        UserAdminService service = operations();

        assertThat(service.resume(ACTOR, ORIGIN, TARGET)).isEqualTo(new OperationResult.OperatorNotAdmin());
        assertThat(service.resetLoginFailures(ACTOR, ORIGIN, TARGET)).isEqualTo(new OperationResult.OperatorNotAdmin());
        assertThat(service.resume(ACTOR, ORIGIN, TARGET)).isEqualTo(new OperationResult.OperatorNotAdmin());
        assertNoWrite();
        verify(publisher, times(3)).publishEvent(any(UserAdminAuditEvent.class));
    }

    @Test
    @DisplayName("granting and revoking call setAdmin with the right flag and touch no token")
    void grantAndRevoke() {
        givenAdminRows(target(false, false), ACTOR);
        assertThat(run(AdminOperation.GRANT_ADMIN)).isEqualTo(new OperationResult.Done());
        verify(userAccounts).setAdmin(TARGET, true);

        givenAdminRows(target(true, false), ACTOR, TARGET);
        assertThat(run(AdminOperation.REVOKE_ADMIN)).isEqualTo(new OperationResult.Done());
        verify(userAccounts).setAdmin(TARGET, false);

        verifyNoInteractions(revocations);
        verify(userAccounts, never()).setSuspended(anyLong(), anyBoolean());
        assertThat(recording.commits()).isEqualTo(2);
    }

    @Test
    @DisplayName("suspending sets the suspension and then revokes every refresh token, and emits one success event")
    void suspendSucceeds() {
        givenAdminRows(target(true, false), ACTOR, TARGET);

        assertThat(run(AdminOperation.SUSPEND)).isEqualTo(new OperationResult.Done());

        InOrder order = inOrder(userAccounts, revocations, publisher);
        order.verify(userAccounts).setSuspended(TARGET, true);
        order.verify(revocations).revokeAllRefreshTokens(TARGET);
        order.verify(publisher).publishEvent(any(UserAdminAuditEvent.class));
        verify(userAccounts, never()).setAdmin(anyLong(), anyBoolean());
        verify(locks, never()).completeFailureReset(anyLong());
        UserAdminAuditEvent event = publishedEvent();
        assertThat(event.succeeded()).isTrue();
        assertThat(event.failure()).isNull();
        assertCommittedWithoutMark();
    }

    /** 行の値に見立てた見分けやすい文（例外の連なりの文に入りうる値）。 */
    private static final String ROW_VALUE = "row-value-7f3a9c@example.com";

    @Test
    @DisplayName(
            "a lock timeout while revoking the refresh tokens on suspend is Busy with rollback-only and no event (R-01)")
    void suspendRevocationLockTimeoutIsBusy() {
        givenAdminRows(target(false, false), ACTOR);
        CannotAcquireLockException timeout =
                new CannotAcquireLockException(ROW_VALUE, new SQLException(ROW_VALUE, "HYT00", 50200));
        doThrow(timeout).when(revocations).revokeAllRefreshTokens(TARGET);

        try (LogEvents logs = LogEvents.capture(UserAdminService.class)) {
            assertThat(run(AdminOperation.SUSPEND)).isEqualTo(new OperationResult.Busy());

            assertThat(logs.list()).singleElement().satisfies(warn -> {
                assertThat(warn.getLevel()).isEqualTo(Level.WARN);
                assertThat(warn.getThrowableProxy()).as("例外そのものはログに渡さない").isNull();
                assertThat(keyValues(warn))
                        .containsExactlyInAnyOrderEntriesOf(Map.of(
                                "lockKind",
                                UserAdminService.REFRESH_TOKEN_ROWS,
                                "exceptionClass",
                                CannotAcquireLockException.class.getName()));
                assertThat(warn.getFormattedMessage()).doesNotContain(ROW_VALUE);
            });
        }
        InOrder order = inOrder(userAccounts, revocations);
        order.verify(userAccounts).setSuspended(TARGET, true);
        order.verify(revocations).revokeAllRefreshTokens(TARGET);
        assertThat(recording.rollbackOnlyMarks()).isEqualTo(1);
        assertThat(recording.rollbacks()).isEqualTo(1);
        assertThat(recording.commits()).isZero();
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("a failure while revoking that is not a lock failure is thrown as is and emits no event (R-01)")
    void suspendRevocationOtherFailureIsThrown() {
        givenAdminRows(target(false, false), ACTOR);
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("other", new SQLException("other", "23505", 23505));
        doThrow(failure).when(revocations).revokeAllRefreshTokens(TARGET);

        try (LogEvents logs = LogEvents.capture(UserAdminService.class)) {
            assertThatThrownBy(() -> run(AdminOperation.SUSPEND)).isSameAs(failure);
            assertThat(logs.list()).isEmpty();
        }
        assertThat(recording.rollbackOnlyMarks()).isZero();
        assertThat(recording.rollbacks()).as("例外で巻き戻る").isEqualTo(1);
        assertThat(recording.commits()).isZero();
        verifyNoInteractions(publisher);
    }

    private static Map<String, String> keyValues(ILoggingEvent event) {
        return event.getKeyValuePairs() == null
                ? Map.of()
                : event.getKeyValuePairs().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                pair -> pair.key, pair -> String.valueOf(pair.value)));
    }

    @Test
    @DisplayName("resuming locks only the target row, lifts the suspension and does not restore tokens")
    void resumeSucceeds() {
        when(userAccounts.lockUserRow(TARGET)).thenReturn(new UserRowLock.Locked(Optional.of(target(true, true))));
        when(userAccounts.findAdminSummary(ACTOR)).thenReturn(Optional.of(actor(true, false)));

        assertThat(run(AdminOperation.RESUME)).isEqualTo(new OperationResult.Done());

        verify(userAccounts).setSuspended(TARGET, false);
        verify(userAccounts, never()).lockAdminRowsInIdOrder(anyLong());
        verifyNoInteractions(revocations, barrier);
        assertThat(publishedEvent().operation()).isEqualTo(AdminOperation.RESUME);
    }

    @Test
    @DisplayName("resuming a user who is not suspended is NO_CHANGE and a missing user is USER_NOT_FOUND")
    void resumeRejections() {
        when(userAccounts.lockUserRow(TARGET))
                .thenReturn(
                        new UserRowLock.Locked(Optional.of(target(false, false))),
                        new UserRowLock.Locked(Optional.empty()));
        UserAdminService service = operations();

        assertThat(service.resume(ACTOR, ORIGIN, TARGET))
                .isEqualTo(new OperationResult.Rejected(RejectionReason.NO_CHANGE));
        assertThat(service.resume(ACTOR, ORIGIN, TARGET))
                .isEqualTo(new OperationResult.Rejected(RejectionReason.USER_NOT_FOUND));
        assertNoWrite();
        verify(userAccounts, never()).findAdminSummary(ACTOR);
    }

    @Test
    @DisplayName("resetting writes through the second step, also for oneself and for a suspended target")
    void resetSucceeds() {
        when(userAccounts.findAdminSummary(ACTOR)).thenReturn(Optional.of(actor(true, false)));
        when(locks.prepareFailureReset(anyLong())).thenReturn(new LoginFailureResetPreparation.Ready());
        when(userAccounts.findAdminSummary(TARGET)).thenReturn(Optional.of(target(false, true)));
        UserAdminService service = operations();

        assertThat(service.resetLoginFailures(ACTOR, ORIGIN, TARGET)).isEqualTo(new OperationResult.Done());
        assertThat(service.resetLoginFailures(ACTOR, ORIGIN, ACTOR)).isEqualTo(new OperationResult.Done());

        InOrder order = inOrder(userAccounts, locks);
        order.verify(userAccounts).findAdminSummary(TARGET);
        order.verify(locks).prepareFailureReset(TARGET);
        order.verify(userAccounts).findAdminSummary(ACTOR);
        order.verify(locks).completeFailureReset(TARGET);
        verify(locks).completeFailureReset(ACTOR);
        verify(userAccounts, never()).setSuspended(anyLong(), anyBoolean());
        verifyNoInteractions(barrier, revocations);
    }

    @Test
    @DisplayName("resetting a missing or negative user never calls the first step, and NothingToReset is NO_CHANGE")
    void resetRejections() {
        when(userAccounts.findAdminSummary(anyLong())).thenReturn(Optional.empty());
        UserAdminService service = operations();

        assertThat(service.resetLoginFailures(ACTOR, ORIGIN, TARGET))
                .isEqualTo(new OperationResult.Rejected(RejectionReason.USER_NOT_FOUND));
        assertThat(service.resetLoginFailures(ACTOR, ORIGIN, -3))
                .isEqualTo(new OperationResult.Rejected(RejectionReason.USER_NOT_FOUND));
        verify(locks, never()).prepareFailureReset(anyLong());

        when(userAccounts.findAdminSummary(TARGET)).thenReturn(Optional.of(target(false, false)));
        when(locks.prepareFailureReset(TARGET)).thenReturn(new LoginFailureResetPreparation.NothingToReset());
        assertThat(service.resetLoginFailures(ACTOR, ORIGIN, TARGET))
                .isEqualTo(new OperationResult.Rejected(RejectionReason.NO_CHANGE));
        verify(locks, never()).completeFailureReset(anyLong());
        verify(userAccounts, never()).findAdminSummary(ACTOR);
    }

    @Test
    @DisplayName("a missing origin is refused before any transaction starts")
    void originIsRequired() {
        UserAdminService service = operations();

        assertThatThrownBy(() -> service.grantAdmin(ACTOR, null, TARGET)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.resume(ACTOR, null, TARGET)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.resetLoginFailures(ACTOR, null, TARGET))
                .isInstanceOf(NullPointerException.class);
        assertThat(recording.definitions()).isEmpty();
    }
}
