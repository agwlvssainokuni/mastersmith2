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
package cherry.mastersmith.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.testsupport.LogEvents;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PessimisticLockException;
import jakarta.persistence.QueryTimeoutException;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

class RowLockFailuresTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RowLockFailuresTest.class);

    /** 行の値に見立てた、出力の中で見分けやすい文字。 */
    private static final String ROW_VALUE = "row-value-7f3a@example.com";

    @Test
    @DisplayName("JPA lock timeout, pessimistic lock and query timeout exceptions are lock failures")
    void jpaTypesAreLockFailures() {
        assertThat(RowLockFailures.isLockFailure(new LockTimeoutException(ROW_VALUE)))
                .isTrue();
        assertThat(RowLockFailures.isLockFailure(new PessimisticLockException(ROW_VALUE)))
                .isTrue();
        assertThat(RowLockFailures.isLockFailure(new QueryTimeoutException(ROW_VALUE)))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {50200, 40001})
    @DisplayName("an SQLException with a lock error code anywhere in the cause chain is a lock failure")
    void errorCodesInTheChainAreLockFailures(int errorCode) {
        SQLException sql = new SQLException(ROW_VALUE, "XXXXX", errorCode);
        PersistenceException wrapped = new PersistenceException("outer", new RuntimeException("middle", sql));

        assertThat(RowLockFailures.isLockFailure(wrapped)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HYT00", "40001"})
    @DisplayName("an SQLException with a lock SQLState is a lock failure")
    void sqlStatesAreLockFailures(String sqlState) {
        assertThat(RowLockFailures.isLockFailure(new SQLException(ROW_VALUE, sqlState, 0)))
                .isTrue();
    }

    @Test
    @DisplayName("a lock failure translated by Spring into CannotAcquireLockException is still a lock failure")
    void springTranslatedExceptionsAreLockFailures() {
        SQLException sql = new SQLException(ROW_VALUE, "HYT00", 50200);
        CannotAcquireLockException translated =
                new CannotAcquireLockException("could not obtain lock", new RuntimeException("hibernate", sql));

        assertThat(RowLockFailures.isLockFailure(translated)).isTrue();
    }

    @Test
    @DisplayName("other error codes such as a unique violation (23505) are not lock failures")
    void otherErrorCodesAreNotLockFailures() {
        SQLException unique = new SQLException(ROW_VALUE, "23505", 23505);

        assertThat(RowLockFailures.isLockFailure(new DataIntegrityViolationException("dup", unique)))
                .isFalse();
        assertThat(RowLockFailures.isLockFailure(new SQLException(ROW_VALUE))).isFalse();
    }

    @Test
    @DisplayName("a chain without an SQLException or lock type, and null, are not lock failures")
    void chainsWithoutLockCausesAreNotLockFailures() {
        assertThat(RowLockFailures.isLockFailure(new PersistenceException("x", new IllegalStateException("y"))))
                .isFalse();
        assertThat(RowLockFailures.isLockFailure(null)).isFalse();
    }

    @Test
    @DisplayName("a cyclic cause chain does not loop forever")
    void cyclicChainsTerminate() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.initCause(second);

        assertThat(RowLockFailures.isLockFailure(first)).isFalse();
    }

    @Test
    @DisplayName("warn logs only the lock kind and the exception class, without the exception or its message")
    void warnDoesNotPassTheException() {
        LockTimeoutException failure = new LockTimeoutException(
                ROW_VALUE, new RuntimeException(ROW_VALUE, new SQLException(ROW_VALUE, "HYT00", 50200)));

        try (LogEvents events = LogEvents.capture(RowLockFailuresTest.class)) {
            RowLockFailures.warn(LOGGER, "LOGIN_ATTEMPT_ROW", failure);

            List<ILoggingEvent> logged = events.list();
            assertThat(logged).hasSize(1);
            ILoggingEvent event = logged.getFirst();
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getFormattedMessage())
                    .isEqualTo(RowLockFailures.WARN_MESSAGE)
                    .doesNotContain(ROW_VALUE);
            assertThat(event.getKeyValuePairs())
                    .extracting(pair -> pair.key)
                    .containsExactly("lockKind", "exceptionClass");
            assertThat(event.getKeyValuePairs())
                    .extracting(KeyValuePair::toString)
                    .noneMatch(text -> text.contains(ROW_VALUE));
            assertThat(event.getKeyValuePairs())
                    .extracting(pair -> String.valueOf(pair.value))
                    .containsExactly("LOGIN_ATTEMPT_ROW", LockTimeoutException.class.getName());
        }
    }
}
