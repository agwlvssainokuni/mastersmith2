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
package cherry.mastersmith.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.persistence.RowLockAttempt;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** 利用者の行の排他の DB アクセスの単体テスト（上限切れを本体の中で受けて Busy にすること。{@code reliability-design.md} 5.2）。 */
class UserRowLockRepositoryTest {

    /** 行の値に見立てた、出力の中で見分けやすい文字。 */
    private static final String ROW_VALUE = "row-value-leak-check-7f3a@example.com";

    private EntityManager entityManager;

    private TypedQuery<Long> adminRowsQuery;

    private TypedQuery<Long> userRowQuery;

    private UserRowLockRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        entityManager = mock(EntityManager.class);
        adminRowsQuery = mock(TypedQuery.class, RETURNS_SELF);
        userRowQuery = mock(TypedQuery.class, RETURNS_SELF);
        when(entityManager.createQuery(contains("u.adminFlag = true or u.userId = :target"), any()))
                .thenAnswer(invocation -> adminRowsQuery);
        when(entityManager.createQuery(contains("where u.userId = :userId"), any()))
                .thenAnswer(invocation -> userRowQuery);
        repository = new UserRowLockRepository(entityManager);
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
    @DisplayName("a lock failure of either lock becomes Busy and no exception leaves the method")
    void lockFailuresBecomeBusy(PersistenceException failure) {
        when(adminRowsQuery.getResultList()).thenThrow(failure);
        when(userRowQuery.getResultList()).thenThrow(failure);

        assertThat(repository.lockAdminRowsAndTarget(7L)).isInstanceOf(RowLockAttempt.Busy.class);
        assertThat(repository.lockUserRow(7L)).isInstanceOf(RowLockAttempt.Busy.class);
    }

    @Test
    @DisplayName("other persistence exceptions are thrown as they are")
    void otherExceptionsPassThrough() {
        PersistenceException other = new PersistenceException("other", new IllegalStateException("x"));
        when(adminRowsQuery.getResultList()).thenThrow(other);
        when(userRowQuery.getResultList()).thenThrow(other);

        assertThatThrownBy(() -> repository.lockAdminRowsAndTarget(7L)).isSameAs(other);
        assertThatThrownBy(() -> repository.lockUserRow(7L)).isSameAs(other);
    }

    @Test
    @DisplayName("acquired locks return the locked ids, and the user lock tells whether the user exists")
    void acquired() {
        when(adminRowsQuery.getResultList()).thenReturn(List.of(1L, 3L, 7L));
        when(userRowQuery.getResultList()).thenReturn(List.of(7L), List.of());

        assertThat(repository.lockAdminRowsAndTarget(7L)).isEqualTo(RowLockAttempt.acquired(List.of(1L, 3L, 7L)));
        assertThat(repository.lockUserRow(7L)).isEqualTo(RowLockAttempt.acquired(true));
        assertThat(repository.lockUserRow(8L)).isEqualTo(RowLockAttempt.acquired(false));
    }

    @Test
    @DisplayName("both locks use the pessimistic write lock, the three-second limit and named parameters")
    void lockSettings() {
        when(adminRowsQuery.getResultList()).thenReturn(List.of());
        when(userRowQuery.getResultList()).thenReturn(List.of());

        repository.lockAdminRowsAndTarget(7L);
        repository.lockUserRow(8L);

        verify(adminRowsQuery).setLockMode(LockModeType.PESSIMISTIC_WRITE);
        verify(adminRowsQuery).setHint("jakarta.persistence.lock.timeout", UserRowLockRepository.LOCK_TIMEOUT_MILLIS);
        verify(adminRowsQuery).setParameter("target", 7L);
        verify(userRowQuery).setLockMode(LockModeType.PESSIMISTIC_WRITE);
        verify(userRowQuery).setHint("jakarta.persistence.lock.timeout", UserRowLockRepository.LOCK_TIMEOUT_MILLIS);
        verify(userRowQuery).setParameter("userId", 8L);
        assertThat(UserRowLockRepository.LOCK_TIMEOUT_MILLIS).isEqualTo(3000);
    }

    @Test
    @DisplayName("a lock failure is logged once as WARN with only the lock kind and the class name")
    void lockFailureIsLoggedWithoutValues() {
        when(adminRowsQuery.getResultList())
                .thenThrow(new LockTimeoutException(ROW_VALUE, new SQLException(ROW_VALUE, "HYT00", 50200)));
        when(userRowQuery.getResultList()).thenThrow(new PessimisticLockException(ROW_VALUE));

        try (LogEvents events = LogEvents.capture(UserRowLockRepository.class)) {
            repository.lockAdminRowsAndTarget(7L);
            repository.lockUserRow(7L);

            List<ILoggingEvent> logged = events.list();
            assertThat(logged).hasSize(2).allSatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getFormattedMessage()).doesNotContain(ROW_VALUE);
            });
            assertThat(logged.get(0).getKeyValuePairs())
                    .extracting(pair -> pair.key + "=" + pair.value)
                    .containsExactly("lockKind=ADMIN_ROWS", "exceptionClass=" + LockTimeoutException.class.getName());
            assertThat(logged.get(1).getKeyValuePairs())
                    .extracting(pair -> pair.key + "=" + pair.value)
                    .containsExactly("lockKind=USER_ROW", "exceptionClass=" + PessimisticLockException.class.getName());
        }
    }
}
