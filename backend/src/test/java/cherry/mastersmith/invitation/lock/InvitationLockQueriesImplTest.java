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
package cherry.mastersmith.invitation.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.persistence.RowLockUnavailableException;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.invitation.domain.Invitation;
import cherry.mastersmith.invitation.domain.InvitationEmail;
import cherry.mastersmith.invitation.domain.InvitationState;
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
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 招待の行の排他の読み取りの断片の単体テスト（排他の待ちの上限切れを本体の中で受けること。U3 の E2〜E4）。 */
class InvitationLockQueriesImplTest {

    /** 行の値に見立てた、出力の中で見分けやすい文字。 */
    private static final String ROW_VALUE = "invitation-row-7f3a@example.com";

    private static final InvitationEmail EMAIL = new InvitationEmail("hanako@example.com");

    private static final byte[] TOKEN_HASH = new byte[32];

    private EntityManager entityManager;

    private TypedQuery<Invitation> query;

    private InvitationLockQueriesImpl queries;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        entityManager = mock(EntityManager.class);
        query = mock(TypedQuery.class, RETURNS_SELF);
        when(entityManager.createQuery(anyString(), any(Class.class))).thenAnswer(invocation -> query);
        queries = new InvitationLockQueriesImpl(entityManager);
    }

    /** 3つのメソッドの呼び方。 */
    static Stream<Arguments> methods() {
        return Stream.of(
                Arguments.of("findByEmailAndStateForUpdate", (Function<InvitationLockQueries, Optional<Invitation>>)
                        q -> q.findByEmailAndStateForUpdate(EMAIL, InvitationState.PENDING)),
                Arguments.of("findByIdForUpdate", (Function<InvitationLockQueries, Optional<Invitation>>)
                        q -> q.findByIdForUpdate(5L)),
                Arguments.of("findByTokenHashForUpdate", (Function<InvitationLockQueries, Optional<Invitation>>)
                        q -> q.findByTokenHashForUpdate(TOKEN_HASH)));
    }

    static Stream<Arguments> methodsAndLockFailures() {
        List<PersistenceException> failures = List.of(
                new LockTimeoutException(ROW_VALUE),
                new PessimisticLockException(ROW_VALUE),
                new QueryTimeoutException(ROW_VALUE),
                new PersistenceException(ROW_VALUE, new SQLException(ROW_VALUE, "HYT00", 50200)),
                new PersistenceException(ROW_VALUE, new SQLException(ROW_VALUE, "40001", 40001)));
        return methods()
                .flatMap(method ->
                        failures.stream().map(failure -> Arguments.of(method.get()[0], method.get()[1], failure)));
    }

    @ParameterizedTest(name = "{0} {2}")
    @MethodSource("methodsAndLockFailures")
    @DisplayName("a lock failure becomes the value-free exception and the original does not leave the method")
    void lockFailuresAreReplaced(
            String name, Function<InvitationLockQueries, Optional<Invitation>> call, PersistenceException failure) {
        when(query.getResultList()).thenThrow(failure);

        assertThatThrownBy(() -> call.apply(queries))
                .isInstanceOf(RowLockUnavailableException.class)
                .hasNoCause()
                .hasMessageNotContaining(ROW_VALUE)
                .satisfies(e -> assertThat(((RowLockUnavailableException) e).getLockKind())
                        .isEqualTo("INVITATION_ROW"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("methods")
    @DisplayName("other persistence exceptions are thrown as they are")
    void otherExceptionsPassThrough(String name, Function<InvitationLockQueries, Optional<Invitation>> call) {
        PersistenceException other = new PersistenceException("other", new SQLException("dup", "23505", 23505));
        when(query.getResultList()).thenThrow(other);

        assertThatThrownBy(() -> call.apply(queries)).isSameAs(other);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("methods")
    @DisplayName("the row is read with a pessimistic write lock and the three-second limit, and empty when missing")
    void readsWithLock(String name, Function<InvitationLockQueries, Optional<Invitation>> call) {
        Invitation invitation = mock(Invitation.class);
        when(query.getResultList()).thenReturn(List.of(invitation), List.of());

        assertThat(call.apply(queries)).containsSame(invitation);
        assertThat(call.apply(queries)).isEmpty();
        verify(query, times(2)).setLockMode(LockModeType.PESSIMISTIC_WRITE);
        verify(query, times(2))
                .setHint("jakarta.persistence.lock.timeout", InvitationLockQueriesImpl.LOCK_TIMEOUT_MILLIS);
    }

    @Test
    @DisplayName("the email is bound as its value only inside the method, together with the state")
    void emailParameter() {
        when(query.getResultList()).thenReturn(List.of());

        queries.findByEmailAndStateForUpdate(EMAIL, InvitationState.PENDING);

        verify(query).setParameter("email", "hanako@example.com");
        verify(query).setParameter("state", InvitationState.PENDING);
    }

    @Test
    @DisplayName("a lock failure is logged once as WARN with only the lock kind and the class name")
    void lockFailureIsLoggedWithoutValues() {
        when(query.getResultList()).thenThrow(new PessimisticLockException(ROW_VALUE));

        try (LogEvents events = LogEvents.capture(InvitationLockQueriesImpl.class)) {
            assertThatThrownBy(() -> queries.findByIdForUpdate(5L)).isInstanceOf(RowLockUnavailableException.class);

            List<ILoggingEvent> logged = events.list();
            assertThat(logged).hasSize(1);
            assertThat(logged.getFirst().getLevel()).isEqualTo(Level.WARN);
            assertThat(logged.getFirst().getThrowableProxy()).isNull();
            assertThat(logged.getFirst().getKeyValuePairs())
                    .extracting(pair -> pair.key + "=" + pair.value)
                    .containsExactly(
                            "lockKind=INVITATION_ROW", "exceptionClass=" + PessimisticLockException.class.getName());
        }
    }
}
