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
package cherry.mastersmith.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.auth.domain.LoginAttemptState;
import cherry.mastersmith.common.persistence.RowLockAttempt;
import cherry.mastersmith.common.persistence.RowLockUnavailableException;
import cherry.mastersmith.common.testsupport.LogEvents;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PessimisticLockException;
import jakarta.persistence.QueryTimeoutException;
import jakarta.persistence.TypedQuery;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** ロックの状態の DB アクセスの単体テスト（排他の待ちの上限切れを本体の中で受けること。U3 の E1）。 */
class LoginAttemptStateRepositoryTest {

    /** 行の値に見立てた、出力の中で見分けやすい文字。 */
    private static final String ROW_VALUE = "row-value-locked-until-2026-10-02T09:41:07Z";

    private EntityManager entityManager;

    private TypedQuery<LoginAttemptState> lockQuery;

    private LoginAttemptStateRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        entityManager = mock(EntityManager.class);
        lockQuery = mock(TypedQuery.class, RETURNS_SELF);
        when(entityManager.createQuery(eq("select s from LoginAttemptState s where s.subjectId = :id"), any()))
                .thenAnswer(invocation -> lockQuery);
        repository = new LoginAttemptStateRepository(entityManager);
    }

    static Stream<PersistenceException> lockFailures() {
        return Stream.of(
                new LockTimeoutException(ROW_VALUE),
                new PessimisticLockException(ROW_VALUE),
                new QueryTimeoutException(ROW_VALUE),
                new PersistenceException(ROW_VALUE, new SQLException(ROW_VALUE, "HYT00", 50200)),
                new PersistenceException(ROW_VALUE, new SQLException(ROW_VALUE, "40001", 40001)));
    }

    @ParameterizedTest
    @MethodSource("lockFailures")
    @DisplayName("a lock failure becomes a value-free exception from lockForUpdate and Busy from tryLockForUpdate")
    void lockFailuresAreReplaced(PersistenceException failure) {
        when(lockQuery.getResultList()).thenThrow(failure);

        assertThatThrownBy(() -> repository.lockForUpdate(7L))
                .isInstanceOf(RowLockUnavailableException.class)
                .hasNoCause()
                .hasMessageNotContaining(ROW_VALUE)
                .satisfies(e -> assertThat(((RowLockUnavailableException) e).getLockKind())
                        .isEqualTo("LOGIN_ATTEMPT_ROW"));
        assertThat(repository.tryLockForUpdate(7L)).isInstanceOf(RowLockAttempt.Busy.class);
    }

    @Test
    @DisplayName("other persistence exceptions are thrown as they are from both methods")
    void otherExceptionsPassThrough() {
        PersistenceException other = new PersistenceException("other", new IllegalStateException("x"));
        when(lockQuery.getResultList()).thenThrow(other);

        assertThatThrownBy(() -> repository.lockForUpdate(7L)).isSameAs(other);
        assertThatThrownBy(() -> repository.tryLockForUpdate(7L)).isSameAs(other);
    }

    @Test
    @DisplayName("a held row is returned as acquired, and a missing row as acquired and empty")
    void acquiredRows() {
        LoginAttemptState row = mock(LoginAttemptState.class);
        when(lockQuery.getResultList()).thenReturn(List.of(row), List.of(), List.of(row));

        assertThat(repository.tryLockForUpdate(7L)).isEqualTo(RowLockAttempt.acquired(Optional.of(row)));
        assertThat(repository.tryLockForUpdate(8L)).isEqualTo(RowLockAttempt.acquired(Optional.empty()));
        assertThat(repository.lockForUpdate(7L)).containsSame(row);
    }

    @Test
    @DisplayName("a lock failure is logged once as WARN with only the lock kind and the class name")
    void lockFailureIsLoggedWithoutValues() {
        when(lockQuery.getResultList())
                .thenThrow(new LockTimeoutException(ROW_VALUE, new SQLException(ROW_VALUE, "HYT00", 50200)));

        try (LogEvents events = LogEvents.capture(LoginAttemptStateRepository.class)) {
            assertThatThrownBy(() -> repository.lockForUpdate(7L)).isInstanceOf(RowLockUnavailableException.class);

            List<ILoggingEvent> logged = events.list();
            assertThat(logged).hasSize(1);
            assertThat(logged.getFirst().getLevel()).isEqualTo(Level.WARN);
            assertThat(logged.getFirst().getThrowableProxy()).isNull();
            assertThat(logged.getFirst().getKeyValuePairs())
                    .extracting(pair -> pair.key + "=" + pair.value)
                    .containsExactly(
                            "lockKind=LOGIN_ATTEMPT_ROW", "exceptionClass=" + LockTimeoutException.class.getName())
                    .noneMatch(text -> text.contains(ROW_VALUE));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("when every dummy row is held and the waited dummy row times out, the value-free exception is thrown")
    void dummyFallbackTimesOut() {
        TypedQuery<LoginAttemptState> skipQuery = mock(TypedQuery.class, RETURNS_SELF);
        when(entityManager.createQuery(contains("s.subjectId < 0"), any())).thenAnswer(invocation -> skipQuery);
        when(skipQuery.getResultList()).thenReturn(List.of());
        when(lockQuery.getResultList()).thenThrow(new LockTimeoutException(ROW_VALUE));

        assertThatThrownBy(() -> repository.lockDummyForUpdate())
                .isInstanceOf(RowLockUnavailableException.class)
                .hasNoCause();
    }

    @Test
    @DisplayName("the lock query keeps the pessimistic write lock and the three-second limit")
    void lockQuerySettings() {
        when(lockQuery.getResultList()).thenReturn(List.of());

        repository.lockForUpdate(7L);

        verify(lockQuery).setLockMode(LockModeType.PESSIMISTIC_WRITE);
        verify(lockQuery).setHint("jakarta.persistence.lock.timeout", LoginAttemptStateRepository.LOCK_TIMEOUT_MILLIS);
        verify(lockQuery).setParameter("id", 7L);
        verify(entityManager, never()).createQuery(anyString());
    }
}
