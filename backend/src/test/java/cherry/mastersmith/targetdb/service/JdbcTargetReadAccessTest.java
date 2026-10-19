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
package cherry.mastersmith.targetdb.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.targetdb.config.TargetDbProperties;
import cherry.mastersmith.targetdb.config.TargetDbSettings;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetReadOutcome;
import cherry.mastersmith.targetdb.domain.TargetReadSession;
import cherry.mastersmith.targetdb.domain.TargetReadUnexpectedException;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** 業務データの読み取りの接続を貸す口（BR8.1・BR8.2、F2: A、NFR 設計のレビュー R-02・R-04）のモックの JDBC での単体テスト。 */
class JdbcTargetReadAccessTest {

    private static final String HOST = "secret-host-" + TestDatabase.randomSecret();

    private static final String PASSWORD = TestDatabase.randomSecret();

    private HikariDataSource dataSource;

    private Connection connection;

    private JdbcTargetReadAccess access;

    private static TargetDbSettings usable() {
        return TargetDbSettings.inspect(new TargetDbProperties(
                "mysql", HOST, "3306", "business", "sales", "reader", PASSWORD, null, null, null));
    }

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = mock(HikariDataSource.class);
        connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getNetworkTimeout()).thenReturn(25_000);
        access = new JdbcTargetReadAccess(usable(), Optional.of(dataSource));
    }

    @AfterEach
    void tearDown() {
        access.close();
    }

    @Test
    @DisplayName("without a usable setting or a data source the work is never called")
    void notConfigured() {
        JdbcTargetReadAccess notConfigured =
                new JdbcTargetReadAccess(new TargetDbSettings.NotConfigured(), Optional.of(dataSource));
        JdbcTargetReadAccess noDataSource = new JdbcTargetReadAccess(usable(), Optional.empty());
        try {
            assertThat(notConfigured.withRead(ReadPurpose.LIST, session -> fail()))
                    .isEqualTo(new TargetReadOutcome.NotConfigured<>());
            assertThat(noDataSource.withRead(ReadPurpose.DETAIL, session -> fail()))
                    .isEqualTo(new TargetReadOutcome.NotConfigured<>());
            assertThat(notConfigured.product()).isEmpty();
        } finally {
            notConfigured.close();
            noDataSource.close();
        }
    }

    @Test
    @DisplayName("a failure to borrow is unavailable without its message and the work is not called")
    void borrowFailure() throws SQLException {
        when(dataSource.getConnection())
                .thenThrow(new SQLTransientConnectionException("host " + HOST + " refused", "08001", 0));

        TargetReadOutcome<String> outcome = access.withRead(ReadPurpose.LIST, session -> fail());

        assertThat(outcome)
                .isEqualTo(new TargetReadOutcome.Unavailable<String>(
                        "08001", 0, "java.sql.SQLTransientConnectionException"));
        assertThat(outcome.toString()).doesNotContain(HOST);
    }

    @Test
    @DisplayName("list and detail lend the connection with a 10 second network timeout and restore the original")
    void lendsWithNetworkTimeout() throws SQLException {
        AtomicReference<TargetReadSession> lent = new AtomicReference<>();

        TargetReadOutcome<String> outcome = access.withRead(ReadPurpose.DETAIL, session -> {
            lent.set(session);
            return "読んだ";
        });

        assertThat(outcome).isEqualTo(new TargetReadOutcome.Done<>("読んだ"));
        assertThat(lent.get().product()).isEqualTo(DatabaseProduct.MYSQL);
        assertThat(lent.get().connection()).isSameAs(connection);
        assertThat(lent.get().queryTimeoutSeconds()).isEqualTo(5);
        InOrder order = inOrder(connection);
        order.verify(connection).setNetworkTimeout(any(Executor.class), eq(10_000));
        order.verify(connection).setNetworkTimeout(any(Executor.class), eq(25_000));
        order.verify(connection).close();
        verify(dataSource, never()).evictConnection(any());
    }

    @Test
    @DisplayName("a failure to restore the network timeout evicts the connection before closing it")
    void restoreFailureEvicts() throws SQLException {
        doThrow(new SQLException("closed", "08003"))
                .when(connection)
                .setNetworkTimeout(any(Executor.class), eq(25_000));

        try (LogEvents events = LogEvents.capture(JdbcTargetReadAccess.class)) {
            assertThat(access.withRead(ReadPurpose.LIST, session -> 1)).isEqualTo(new TargetReadOutcome.Done<>(1));

            assertWarnWithClassOnly(events.list());
        }
        InOrder order = inOrder(dataSource, connection);
        order.verify(dataSource).evictConnection(connection);
        order.verify(connection).close();
    }

    @Test
    @DisplayName(
            "an unchecked exception of the work becomes one without message or cause, and the connection is closed")
    void uncheckedFailureOfTheWork() throws SQLException {
        assertThatThrownBy(() -> access.withRead(ReadPurpose.LIST, session -> {
                    throw new NumberFormatException("For input string: \"ペン\"");
                }))
                .isInstanceOfSatisfying(TargetReadUnexpectedException.class, e -> {
                    assertThat(e.getMessage()).isNull();
                    assertThat(e.getCause()).isNull();
                    assertThat(e.exceptionClass()).isEqualTo("NumberFormatException");
                });
        verify(connection).setNetworkTimeout(any(Executor.class), eq(25_000));
        verify(connection).close();
    }

    @Test
    @DisplayName("a failure to set the network timeout is unavailable, evicts and closes, and the work is not called")
    void setTimeoutFailure() throws SQLException {
        doThrow(new SQLException("x", "08S01", 0)).when(connection).setNetworkTimeout(any(Executor.class), anyInt());

        assertThat(access.withRead(ReadPurpose.LIST, session -> fail()))
                .isEqualTo(new TargetReadOutcome.Unavailable<>("08S01", 0, "java.sql.SQLException"));
        verify(dataSource).evictConnection(connection);
        verify(connection).close();
    }

    @Test
    @DisplayName("the schema reading purposes are refused and the product comes from the setting without connecting")
    void purposesAndProduct() throws SQLException {
        assertThatThrownBy(() -> access.withRead(ReadPurpose.GENERATE, session -> 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> access.withRead(ReadPurpose.COMPARE, session -> 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(access.product()).contains(DatabaseProduct.MYSQL);
        verify(dataSource, never()).getConnection();
    }

    @Test
    @DisplayName("a failure to close is logged with the class name only and the result is still returned")
    void closeFailure() throws SQLException {
        doThrow(new SQLException("close " + HOST)).when(connection).close();

        try (LogEvents events = LogEvents.capture(JdbcTargetReadAccess.class)) {
            assertThat(access.withRead(ReadPurpose.LIST, session -> "x")).isEqualTo(new TargetReadOutcome.Done<>("x"));

            assertWarnWithClassOnly(events.list());
        }
    }

    private static void assertWarnWithClassOnly(List<ILoggingEvent> logs) {
        assertThat(logs).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getKeyValuePairs()).singleElement().satisfies(pair -> {
                assertThat(pair.key).isEqualTo("exceptionClass");
                assertThat(pair.value).isEqualTo("java.sql.SQLException");
            });
            assertThat(event.getFormattedMessage()).doesNotContain(HOST);
        });
    }

    private static <T> T fail() {
        throw new AssertionError("処理は呼ばれない");
    }
}
