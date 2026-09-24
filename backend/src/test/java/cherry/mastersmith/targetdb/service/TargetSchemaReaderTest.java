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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.targetdb.config.TargetDbProperties;
import cherry.mastersmith.targetdb.config.TargetDbSettings;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetSchemaResult;
import cherry.mastersmith.targetdb.domain.UnavailableReason;
import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * 読み取りの口の単体テスト（BR1.6・BR1.8・BR1.9、NFR1.1・NFR1.2、NFR4.4）。
 *
 * <p>JDBC の接続・問い合わせは Mockito で差し替え、打ち切り・接続の失敗・途中の失敗を作る。照合の全体の上限は作らない
 * （NFR 設計の承認の場の決定 B）ため、待ちの上限は問い合わせ1回ごとに当たり、回を重ねても減らないことを、差し替えた
 * {@code PreparedStatement} に渡る値で確かめる（sleep や実時刻に頼らない）。
 */
class TargetSchemaReaderTest {

    private static final String HOST = "secret-host-" + TestDatabase.randomSecret();

    private static final String USERNAME = "reader-" + TestDatabase.randomSecret();

    private static final String PASSWORD = TestDatabase.randomSecret();

    private DataSource dataSource;

    private Connection connection;

    private PreparedStatement statement;

    private static TargetDbSettings usable() {
        return usable(null);
    }

    private static TargetDbSettings usable(TargetDbProperties.QueryTimeout queryTimeout) {
        return TargetDbSettings.inspect(new TargetDbProperties(
                "postgresql", HOST, "5432", "business", "sales", USERNAME, PASSWORD, null, queryTimeout, null));
    }

    private JdbcTargetSchemaReader reader() {
        return new JdbcTargetSchemaReader(usable(), Optional.of(dataSource));
    }

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        ResultSet empty = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(empty);
        when(empty.next()).thenReturn(false);
    }

    @Test
    @DisplayName("without a usable setting the result is unconfigured and nothing is connected")
    void unconfigured() {
        TargetDbSettings notConfigured = new TargetDbSettings.NotConfigured();
        TargetDbSettings invalid = new TargetDbSettings.Invalid(List.of(TargetDbSettings.SCHEMA));

        assertThat(new JdbcTargetSchemaReader(notConfigured, Optional.empty()).readSchema(ReadPurpose.GENERATE))
                .isEqualTo(TargetSchemaResult.unconfigured());
        assertThat(new JdbcTargetSchemaReader(invalid, Optional.of(dataSource)).readSchema(ReadPurpose.COMPARE))
                .isEqualTo(TargetSchemaResult.unconfigured());
        assertThat(new JdbcTargetSchemaReader(usable(), Optional.empty()).readSchema(ReadPurpose.COMPARE))
                .isEqualTo(TargetSchemaResult.unconfigured());
        verifyNoInteractions(dataSource);
    }

    @Test
    @DisplayName("generation reads with four queries, each limited to 20 seconds, and closes the connection")
    void generateTimeout() throws SQLException {
        TargetSchemaResult result = reader().readSchema(ReadPurpose.GENERATE);

        assertThat(result)
                .isInstanceOfSatisfying(
                        TargetSchemaResult.Success.class,
                        success -> assertThat(success.schema().tables()).isEmpty());
        verify(connection, times(4)).prepareStatement(anyString());
        verify(statement, times(4)).setQueryTimeout(20);
        verify(statement, times(4)).setString(1, "sales");
        verify(connection).close();
    }

    @Test
    @DisplayName("comparison limits each query to 5 seconds")
    void compareTimeout() throws SQLException {
        assertThat(reader().readSchema(ReadPurpose.COMPARE)).isInstanceOf(TargetSchemaResult.Success.class);

        verify(statement, times(4)).setQueryTimeout(5);
    }

    @Test
    @DisplayName(
            "comparison has no overall limit: each of the four queries gets the full 5 seconds and the read succeeds")
    void compareHasNoOverallLimit() throws SQLException {
        TargetSchemaResult result = reader().readSchema(ReadPurpose.COMPARE);

        assertThat(result).isInstanceOf(TargetSchemaResult.Success.class);
        // 全体の上限があれば、後の問い合わせほど残りが減る。4回とも同じ 5 秒が、問い合わせの前に当たることを確かめる。
        InOrder order = inOrder(statement);
        for (int i = 0; i < 4; i++) {
            order.verify(statement).setQueryTimeout(5);
            order.verify(statement).executeQuery();
        }
        verify(statement, times(4)).executeQuery();
    }

    @Test
    @DisplayName("generation also applies its 20 second limit to every query without an overall limit")
    void generateHasNoOverallLimit() throws SQLException {
        assertThat(reader().readSchema(ReadPurpose.GENERATE)).isInstanceOf(TargetSchemaResult.Success.class);

        InOrder order = inOrder(statement);
        for (int i = 0; i < 4; i++) {
            order.verify(statement).setQueryTimeout(20);
            order.verify(statement).executeQuery();
        }
    }

    @Test
    @DisplayName("configured per-query limits are applied per purpose")
    void configuredPerQueryLimits() throws SQLException {
        TargetDbSettings settings =
                usable(new TargetDbProperties.QueryTimeout(Duration.ofSeconds(30), Duration.ofSeconds(7)));

        new JdbcTargetSchemaReader(settings, Optional.of(dataSource)).readSchema(ReadPurpose.COMPARE);
        verify(statement, times(4)).setQueryTimeout(7);

        new JdbcTargetSchemaReader(settings, Optional.of(dataSource)).readSchema(ReadPurpose.GENERATE);
        verify(statement, times(4)).setQueryTimeout(30);
    }

    @Test
    @DisplayName("query and connection timeouts become TIMEOUT")
    void timeouts() throws SQLException {
        doThrow(new SQLTimeoutException("statement timeout", "70100"))
                .when(statement)
                .executeQuery();
        assertThat(reader().readSchema(ReadPurpose.GENERATE))
                .isEqualTo(TargetSchemaResult.unavailable(UnavailableReason.TIMEOUT));

        doThrow(new SQLException("canceling statement", "57014"))
                .when(statement)
                .executeQuery();
        assertThat(reader().readSchema(ReadPurpose.GENERATE))
                .isEqualTo(TargetSchemaResult.unavailable(UnavailableReason.TIMEOUT));

        doThrow(new SQLException("Communications link failure", "08S01", new SocketTimeoutException("Read timed out")))
                .when(dataSource)
                .getConnection();
        assertThat(reader().readSchema(ReadPurpose.GENERATE))
                .as("接続の途中で相手の応答を待ちきれなかった")
                .isEqualTo(TargetSchemaResult.unavailable(UnavailableReason.TIMEOUT));

        doThrow(new SQLTransientConnectionException("pool timed out", "08001"))
                .when(dataSource)
                .getConnection();
        assertThat(reader().readSchema(ReadPurpose.COMPARE))
                .as("プールの待ちの打ち切りで、その間に接続の失敗が無い＝相手が応答しない")
                .isEqualTo(TargetSchemaResult.unavailable(UnavailableReason.TIMEOUT));
    }

    @Test
    @DisplayName("authentication, connection and other query failures become CONNECTION_FAILED")
    void connectionFailures() throws SQLException {
        doThrow(new SQLTransientConnectionException(
                        "pool timed out", "28P01", new SQLException("password authentication failed", "28P01")))
                .when(dataSource)
                .getConnection();
        assertThat(reader().readSchema(ReadPurpose.GENERATE))
                .isEqualTo(TargetSchemaResult.unavailable(UnavailableReason.CONNECTION_FAILED));

        doThrow(new SQLException("refused", "08001")).when(dataSource).getConnection();
        assertThat(reader().readSchema(ReadPurpose.COMPARE))
                .isEqualTo(TargetSchemaResult.unavailable(UnavailableReason.CONNECTION_FAILED));
    }

    @Test
    @DisplayName("a failure in the middle of reading returns no partial copy")
    void failureInTheMiddle() throws SQLException {
        ResultSet empty = mock(ResultSet.class);
        when(statement.executeQuery())
                .thenReturn(empty)
                .thenReturn(empty)
                .thenThrow(new SQLException("relation does not exist", "42P01"));

        assertThat(reader().readSchema(ReadPurpose.GENERATE))
                .isEqualTo(TargetSchemaResult.unavailable(UnavailableReason.CONNECTION_FAILED));
        verify(statement, times(3)).executeQuery();
        verify(connection).close();
    }

    @Test
    @DisplayName("the warning carries only the purpose, reason and SQLState, never the message, host or user name")
    void warningCarriesKindOnly() throws SQLException {
        String message = "FATAL: password authentication failed for user \"" + USERNAME + "\" at " + HOST;
        doThrow(new SQLException(message, "28P01")).when(dataSource).getConnection();

        try (LogEvents events = LogEvents.capture(JdbcTargetSchemaReader.class)) {
            reader().readSchema(ReadPurpose.GENERATE);

            List<ILoggingEvent> logs = events.list();
            assertThat(logs).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getThrowableProxy()).as("例外（文言とスタックトレース）は出さない").isNull();
                assertThat(event.getKeyValuePairs())
                        .extracting(pair -> pair.key)
                        .containsExactly("purpose", "reason", "sqlState");
                String rendered = event.getFormattedMessage() + event.getKeyValuePairs();
                assertThat(rendered)
                        .contains("CONNECTION_FAILED")
                        .contains("28P01")
                        .doesNotContain(message)
                        .doesNotContain(HOST)
                        .doesNotContain(USERNAME)
                        .doesNotContain(PASSWORD);
            });
        }
    }

    @Test
    @DisplayName("a missing purpose is a programming error")
    void purposeIsRequired() {
        assertThatThrownBy(() -> reader().readSchema(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the per-query limit is converted to whole seconds, dropping fractions and capping at int")
    void toQueryTimeoutSeconds() {
        assertThat(JdbcTargetSchemaReader.toQueryTimeoutSeconds(Duration.ofSeconds(5)))
                .isEqualTo(5);
        assertThat(JdbcTargetSchemaReader.toQueryTimeoutSeconds(Duration.ofMillis(5_900)))
                .isEqualTo(5);
        assertThat(JdbcTargetSchemaReader.toQueryTimeoutSeconds(Duration.ofSeconds(1)))
                .isEqualTo(1);
        assertThat(JdbcTargetSchemaReader.toQueryTimeoutSeconds(Duration.ofDays(100_000)))
                .isEqualTo(Integer.MAX_VALUE);
    }
}
