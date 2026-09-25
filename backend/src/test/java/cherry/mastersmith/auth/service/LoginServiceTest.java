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
package cherry.mastersmith.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import cherry.mastersmith.auth.domain.AuthProblemTypes;
import cherry.mastersmith.auth.domain.AuthenticationEvent;
import cherry.mastersmith.auth.domain.AuthenticationEventType;
import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.auth.domain.LoginAttemptState;
import cherry.mastersmith.auth.domain.LoginFailureReason;
import cherry.mastersmith.auth.domain.RefreshToken;
import cherry.mastersmith.auth.repository.LoginAttemptStateRepository;
import cherry.mastersmith.auth.repository.RefreshTokenRepository;
import cherry.mastersmith.auth.testsupport.TestSigningKeyEnvironmentPostProcessor;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.PasswordVerification;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.service.UserSummary;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class LoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

    private static final ClientInfo CLIENT = new ClientInfo("192.0.2.1", "テスト用のブラウザ", "trace-1");

    private static final UserSummary USER = new UserSummary(7, "user@example.com", false);

    private final UserAccountService userAccountService = mock(UserAccountService.class);

    private final LoginAttemptStateRepository attempts = mock(LoginAttemptStateRepository.class);

    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    private LoginService service;

    @BeforeEach
    void setUp() {
        String key = TestSigningKeyEnvironmentPostProcessor.randomKey(32);
        AuthProperties properties = SigningKeyProviderTest.properties(key);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new LoginService(
                userAccountService,
                attempts,
                refreshTokens,
                new AccessTokenService(new SigningKeyProvider(properties), properties, clock),
                publisher,
                properties,
                clock,
                transactionManager);
    }

    private void givenVerification(UserSummary user, boolean matched) {
        when(userAccountService.verifyPassword(eq("user@example.com"), any(Password.class)))
                .thenReturn(new PasswordVerification("user@example.com", user, matched));
    }

    private void givenState(int failures, Instant lockedUntil) {
        when(attempts.lockForUpdate(7)).thenReturn(Optional.of(new LoginAttemptState(7, failures, lockedUntil)));
    }

    private BusinessException fail() {
        try {
            service.login(new LoginCommand("user@example.com", new Password("パスワード")), CLIENT);
        } catch (BusinessException e) {
            return e;
        }
        throw new AssertionError("ログインが成功してしまった");
    }

    private AuthenticationEvent event() {
        ArgumentCaptor<AuthenticationEvent> captor = ArgumentCaptor.forClass(AuthenticationEvent.class);
        verify(publisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("success resets failures, issues two tokens and publishes LOGIN_SUCCEEDED")
    void success() {
        givenVerification(USER, true);
        givenState(3, null);

        IssuedTokens tokens = service.login(new LoginCommand("user@example.com", new Password("パスワード")), CLIENT);

        verify(attempts).update(7, 0, null);
        assertThat(tokens.accessToken().expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(tokens.refreshToken().value()).hasSize(43);
        assertThat(tokens.user()).isEqualTo(USER);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(event().eventType()).isEqualTo(AuthenticationEventType.LOGIN_SUCCEEDED);
    }

    @Test
    @DisplayName("a wrong password counts one failure and publishes PASSWORD_MISMATCH")
    void mismatch() {
        givenVerification(USER, false);
        givenState(1, null);

        assertThat(fail().getProblemType()).isEqualTo(AuthProblemTypes.AUTHENTICATION_FAILED);
        verify(attempts).update(7, 2, null);
        assertThat(event().failureReason()).isEqualTo(LoginFailureReason.PASSWORD_MISMATCH);
        verify(refreshTokens, never()).save(any());
    }

    @Test
    @DisplayName("the failure that reaches the threshold locks and logs only the user id at INFO")
    void locks() {
        givenVerification(USER, false);
        givenState(4, null);

        try (LogEvents events = LogEvents.capture(LoginService.class)) {
            fail();
            assertThat(events.list()).singleElement().satisfies(log -> {
                assertThat(log.getLevel()).isEqualTo(Level.INFO);
                assertThat(log.getKeyValuePairs().toString()).contains("userId").doesNotContain("user@example.com");
            });
        }
        verify(attempts).update(7, 5, NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("while locked a matching password is rejected and the same values are written once")
    void locked() {
        givenVerification(USER, true);
        Instant until = NOW.plusSeconds(60);
        givenState(5, until);

        fail();

        verify(attempts, times(1)).update(7, 5, until);
        assertThat(event().failureReason()).isEqualTo(LoginFailureReason.ACCOUNT_LOCKED);
    }

    @Test
    @DisplayName("after the unlock time the attempt is judged from zero")
    void afterUnlock() {
        givenVerification(USER, false);
        givenState(5, NOW);

        fail();

        verify(attempts).update(7, 1, null);
    }

    @Test
    @DisplayName("an unknown email reads and writes a dummy row and publishes USER_NOT_FOUND")
    void unknownUser() {
        givenVerification(null, false);
        when(attempts.lockDummyForUpdate()).thenReturn(new LoginAttemptState(-3, 0, null));

        fail();

        verify(attempts).lockDummyForUpdate();
        verify(attempts).update(-3, 0, null);
        verify(attempts, never()).lockForUpdate(anyLong());
        AuthenticationEvent event = event();
        assertThat(event.failureReason()).isEqualTo(LoginFailureReason.USER_NOT_FOUND);
        assertThat(event.userId()).isNull();
    }

    @Test
    @DisplayName("events carry the time, email, client address, user agent and trace id")
    void eventFields() {
        givenVerification(USER, false);
        givenState(0, null);

        fail();

        assertThat(event())
                .isEqualTo(new AuthenticationEvent(
                        AuthenticationEventType.LOGIN_FAILED,
                        NOW,
                        "user@example.com",
                        7L,
                        LoginFailureReason.PASSWORD_MISMATCH,
                        "192.0.2.1",
                        "テスト用のブラウザ",
                        "trace-1"));
    }

    @Test
    @DisplayName("the failure exception is the same regardless of the reason")
    void sameFailure() {
        givenVerification(null, false);
        when(attempts.lockDummyForUpdate()).thenReturn(new LoginAttemptState(-1, 0, null));
        BusinessException notFound = fail();

        givenVerification(USER, false);
        givenState(0, null);
        BusinessException mismatch = fail();

        assertThat(notFound.getProblemType()).isEqualTo(mismatch.getProblemType());
        assertThat(notFound.getDetail()).isNull();
        assertThat(mismatch.getDetail()).isNull();
    }

    @Test
    @DisplayName("a missing lock row is created in its own transaction and the login is decided again")
    void createsMissingRow() {
        givenVerification(USER, true);
        when(attempts.lockForUpdate(7))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new LoginAttemptState(7, 0, null)));

        IssuedTokens tokens = service.login(new LoginCommand("user@example.com", new Password("パスワード")), CLIENT);

        assertThat(tokens.user()).isEqualTo(USER);
        InOrder order = inOrder(transactionManager, attempts);
        order.verify(transactionManager).getTransaction(any());
        order.verify(attempts).lockForUpdate(7);
        order.verify(transactionManager).commit(any());
        order.verify(transactionManager).getTransaction(any());
        order.verify(attempts).createIfAbsent(7);
        order.verify(transactionManager).commit(any());
        order.verify(transactionManager).getTransaction(any());
        order.verify(attempts).lockForUpdate(7);
        order.verify(attempts).update(7, 0, null);
        order.verify(transactionManager).commit(any());
        verify(attempts, times(1)).update(anyLong(), anyInt(), any());
        assertThat(event().eventType()).isEqualTo(AuthenticationEventType.LOGIN_SUCCEEDED);
    }

    @Test
    @DisplayName("a row created at the same time by another login is treated as existing")
    void duplicateRowIsExisting() {
        givenVerification(USER, true);
        when(attempts.lockForUpdate(7))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new LoginAttemptState(7, 0, null)));
        doThrow(new DuplicateKeyException("重複")).when(attempts).createIfAbsent(7);

        Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LoginService.class);
        Level before = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try (LogEvents events = LogEvents.capture(LoginService.class)) {
            IssuedTokens tokens = service.login(new LoginCommand("user@example.com", new Password("パスワード")), CLIENT);

            assertThat(tokens.user()).isEqualTo(USER);
            assertThat(events.list()).singleElement().satisfies(log -> {
                assertThat(log.getLevel()).isEqualTo(Level.DEBUG);
                assertThat(log.getKeyValuePairs().toString()).contains("userId");
            });
        } finally {
            logger.setLevel(before);
        }
        verify(attempts).update(7, 0, null);
        assertThat(event().eventType()).isEqualTo(AuthenticationEventType.LOGIN_SUCCEEDED);
    }

    @Test
    @DisplayName("a wrong password for a user without a row counts as the first failure")
    void missingRowFirstFailure() {
        givenVerification(USER, false);
        when(attempts.lockForUpdate(7))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new LoginAttemptState(7, 0, null)));

        assertThat(fail().getProblemType()).isEqualTo(AuthProblemTypes.AUTHENTICATION_FAILED);

        verify(attempts).createIfAbsent(7);
        verify(attempts, times(1)).update(7, 1, null);
        AuthenticationEvent event = event();
        assertThat(event.eventType()).isEqualTo(AuthenticationEventType.LOGIN_FAILED);
        assertThat(event.failureReason()).isEqualTo(LoginFailureReason.PASSWORD_MISMATCH);
        verify(refreshTokens, never()).save(any());
    }

    @Test
    @DisplayName("a row still missing after creating it fails loudly")
    void rowStillMissing() {
        givenVerification(USER, true);
        when(attempts.lockForUpdate(7)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginCommand("user@example.com", new Password("パスワード")), CLIENT))
                .isInstanceOf(IllegalStateException.class);

        verify(attempts).createIfAbsent(7);
        verify(attempts, never()).update(anyLong(), anyInt(), any());
        verifyNoInteractions(publisher, refreshTokens);
    }
}
