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
import static org.awaitility.Awaitility.await;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.targetdb.config.TargetDbProperties;
import cherry.mastersmith.targetdb.config.TargetDbSettings;
import cherry.mastersmith.targetdb.config.TargetDbWriteSettings;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.TargetWriteFailure;
import cherry.mastersmith.targetdb.domain.TargetWriteOutcome;
import cherry.mastersmith.targetdb.domain.TargetWriteSession;
import cherry.mastersmith.targetdb.domain.TargetWriteStage;
import cherry.mastersmith.targetdb.domain.WriteStep;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * 業務データの書き込みの口の単体テスト（Intent 261016-business-data-views の U2、BR9.3・BR10.1、reliability-design.md の 3節・7節）。
 *
 * <p>組み込みの H2 のメモリの DB を書き込みの DataSource に見立て、口の確定・巻き戻し・セーブポイントの手順だけを確かめる（対象DB の
 * 振る舞いの確かめではない。それは3種類の DB の結合テスト）。確定・巻き戻しの失敗は、管理者を継いだテスト用の部品で起こす。
 */
class JdbcTargetWriteAccessTest {

    /** 例外の文に入れる、値に見立てた文字（結果に出ないことを確かめる）。 */
    private static final String VALUE_IN_MESSAGE = "値-" + TestDatabase.randomSecret();

    private HikariDataSource dataSource;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:w" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setMaximumPoolSize(2);
        dataSource.setMinimumIdle(0);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE item (id INT PRIMARY KEY, name VARCHAR(20))");
        }
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    private static TargetDbWriteSettings usableSettings() {
        TargetDbProperties properties = new TargetDbProperties(
                "postgresql",
                "h",
                "5432",
                "d",
                "s",
                "reader",
                "p",
                null,
                new TargetDbProperties.QueryTimeout(null, null, null, null, null, Duration.ofSeconds(4)),
                null,
                null,
                new TargetDbProperties.Write("writer", TestDatabase.randomSecret(), null));
        return TargetDbWriteSettings.inspect(TargetDbSettings.inspect(properties), properties);
    }

    private JdbcTargetWriteAccess access(PlatformTransactionManager manager) {
        return access(dataSource, manager);
    }

    private static JdbcTargetWriteAccess access(DataSource source, PlatformTransactionManager manager) {
        return new JdbcTargetWriteAccess(usableSettings(), Optional.of(source), Optional.of(manager));
    }

    private static void insert(TargetWriteSession session, int id) {
        try (Statement statement = session.connection().createStatement()) {
            statement.executeUpdate("INSERT INTO item (id, name) VALUES (" + id + ", 'x')");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int count(TargetWriteSession session) {
        try (Statement statement = session.connection().createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM item")) {
            resultSet.next();
            return resultSet.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private int countFromAnotherConnection() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM item")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static TransactionSystemException failureWithValue() {
        return new TransactionSystemException("失敗", new SQLException("接続が切れた " + VALUE_IN_MESSAGE, "08006", 0));
    }

    @Test
    @DisplayName("a commit answer commits once, lends the product and the write limit, and returns the result")
    void commit() throws SQLException {
        AtomicReference<TargetWriteSession> lent = new AtomicReference<>();

        TargetWriteOutcome<String> outcome = access(new DataSourceTransactionManager(dataSource))
                .withWrite(session -> {
                    lent.set(session);
                    insert(session, 1);
                    return new WriteStep.Commit<>("作った");
                });

        assertThat(outcome).isEqualTo(new TargetWriteOutcome.Done<>("作った"));
        assertThat(countFromAnotherConnection()).isEqualTo(1);
        assertThat(lent.get().product()).isEqualTo(DatabaseProduct.POSTGRESQL);
        assertThat(lent.get().queryTimeoutSeconds()).isEqualTo(4);
        assertThat(lent.get().connection().isClosed()).as("接続は口が返した").isTrue();
        assertThat(outcome.toString()).isEqualTo("Done[valueType=String]");
    }

    @Test
    @DisplayName("a rollback answer rolls back and returns the result")
    void rollback() throws SQLException {
        TargetWriteOutcome<String> outcome = access(new DataSourceTransactionManager(dataSource))
                .withWrite(session -> {
                    insert(session, 1);
                    return new WriteStep.Rollback<>("当たった行が 0");
                });

        assertThat(outcome).isEqualTo(new TargetWriteOutcome.Done<>("当たった行が 0"));
        assertThat(countFromAnotherConnection()).isZero();
    }

    @Test
    @DisplayName("an inspect answer goes back to the savepoint, inspects on the same connection, then rolls back all")
    void rollbackToSavepointThenInspect() throws SQLException {
        AtomicReference<Connection> writing = new AtomicReference<>();

        TargetWriteOutcome<Integer> outcome = access(new DataSourceTransactionManager(dataSource))
                .withWrite(session -> {
                    writing.set(session.connection());
                    insert(session, 1);
                    try {
                        session.markSavepoint();
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                    insert(session, 2);
                    return new WriteStep.RollbackToSavepointThenInspect<>(
                            inspecting -> {
                                assertThat(inspecting.connection())
                                        .as("同じ接続で確かめる")
                                        .isSameAs(writing.get());
                                return count(inspecting);
                            },
                            failure -> -1);
                });

        assertThat(outcome).as("セーブポイントの後の行だけが戻っている").isEqualTo(new TargetWriteOutcome.Done<>(1));
        assertThat(countFromAnotherConnection()).as("最後に全体を巻き戻した").isZero();
    }

    @Test
    @DisplayName("an inspect answer without a savepoint answers the failure of going back to the savepoint")
    void inspectWithoutSavepoint() throws SQLException {
        AtomicBoolean inspected = new AtomicBoolean();

        TargetWriteOutcome<String> outcome = access(new DataSourceTransactionManager(dataSource))
                .withWrite(session -> {
                    insert(session, 1);
                    return new WriteStep.RollbackToSavepointThenInspect<>(
                            inspecting -> {
                                inspected.set(true);
                                return "確かめた";
                            },
                            failure -> failure.stage().name());
                });

        assertThat(outcome).isEqualTo(new TargetWriteOutcome.Done<>("ROLLBACK_TO_SAVEPOINT"));
        assertThat(inspected).isFalse();
        assertThat(countFromAnotherConnection()).isZero();
    }

    @Test
    @DisplayName("an unexpected exception in the inspection answers the failure of the inspection")
    void inspectionThrows() throws SQLException {
        TargetWriteOutcome<String> outcome = access(new DataSourceTransactionManager(dataSource))
                .withWrite(session -> {
                    try {
                        session.markSavepoint();
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                    insert(session, 1);
                    return new WriteStep.RollbackToSavepointThenInspect<>(
                            inspecting -> {
                                throw new IllegalArgumentException(VALUE_IN_MESSAGE);
                            },
                            failure -> failure.stage() + ":" + failure.exceptionClass());
                });

        assertThat(outcome).isEqualTo(new TargetWriteOutcome.Done<>("INSPECT:IllegalArgumentException"));
        assertThat(countFromAnotherConnection()).isZero();
    }

    @Test
    @DisplayName("without write settings the work is not called and the outcome is not configured")
    void notConfigured() {
        AtomicBoolean called = new AtomicBoolean();
        JdbcTargetWriteAccess access = new JdbcTargetWriteAccess(
                new TargetDbWriteSettings.NotConfigured(), Optional.empty(), Optional.empty());

        TargetWriteOutcome<String> outcome = access.withWrite(session -> {
            called.set(true);
            return new WriteStep.Commit<>("x");
        });

        assertThat(outcome).isInstanceOf(TargetWriteOutcome.NotConfigured.class);
        assertThat(called).isFalse();
        assertThat(access.product()).isEmpty();
        assertThat(access(new DataSourceTransactionManager(dataSource)).product())
                .contains(DatabaseProduct.POSTGRESQL);
    }

    @Test
    @DisplayName("a connection that cannot be borrowed answers unavailable without calling the work")
    void borrowFailure() {
        try (HikariDataSource unreachable = new HikariDataSource()) {
            unreachable.setJdbcUrl("jdbc:h2:file:" + tempDir.resolve("none") + ";IFEXISTS=TRUE");
            unreachable.setConnectionTimeout(250);
            unreachable.setInitializationFailTimeout(-1);
            AtomicBoolean called = new AtomicBoolean();

            TargetWriteOutcome<String> outcome = access(unreachable, new DataSourceTransactionManager(unreachable))
                    .withWrite(session -> {
                        called.set(true);
                        return new WriteStep.Commit<>("x");
                    });

            assertThat(outcome).isInstanceOf(TargetWriteOutcome.Unavailable.class);
            assertThat(called).isFalse();
        }
    }

    @Test
    @DisplayName("a failed commit answers the commit stage with codes only and the connection is evicted afterwards")
    void commitFailure() throws SQLException {
        DataSourceTransactionManager failingCommit = new DataSourceTransactionManager(dataSource) {
            @Override
            protected void doCommit(DefaultTransactionStatus status) {
                throw failureWithValue();
            }
        };

        TargetWriteOutcome<String> outcome = access(failingCommit).withWrite(session -> {
            insert(session, 1);
            return new WriteStep.Commit<>("作った");
        });

        assertThat(outcome)
                .isEqualTo(new TargetWriteOutcome.Failed<>(
                        new TargetWriteFailure(TargetWriteStage.COMMIT, "08006", 0, "java.sql.SQLException", true)));
        assertThat(outcome.toString()).doesNotContain(VALUE_IN_MESSAGE);
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(dataSource.getHikariPoolMXBean().getTotalConnections())
                        .as("確定に失敗した接続は捨てた")
                        .isZero());
    }

    @Test
    @DisplayName("a failed rollback after a rollback answer still returns the result because nothing was saved")
    void rollbackFailureKeepsTheAnswer() {
        DataSourceTransactionManager failingRollback = new DataSourceTransactionManager(dataSource) {
            @Override
            protected void doRollback(DefaultTransactionStatus status) {
                throw failureWithValue();
            }
        };

        TargetWriteOutcome<String> outcome =
                access(failingRollback).withWrite(session -> new WriteStep.Rollback<>("当たった行が 2"));

        assertThat(outcome).isEqualTo(new TargetWriteOutcome.Done<>("当たった行が 2"));
    }

    @Test
    @DisplayName("a failed final rollback after an inspection answers the failure of the rollback stage")
    void finalRollbackFailureAfterInspection() {
        DataSourceTransactionManager failingRollback = new DataSourceTransactionManager(dataSource) {
            @Override
            protected void doRollback(DefaultTransactionStatus status) {
                throw failureWithValue();
            }
        };

        TargetWriteOutcome<String> outcome = access(failingRollback).withWrite(session -> {
            try {
                session.markSavepoint();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
            return new WriteStep.RollbackToSavepointThenInspect<>(
                    inspecting -> "確かめた", failure -> failure.stage() + ":" + failure.interrupted());
        });

        assertThat(outcome).isEqualTo(new TargetWriteOutcome.Done<>("ROLLBACK:true"));
    }

    @Test
    @DisplayName("an unchecked exception in the work rolls back and answers the work stage without the message")
    void workThrows() throws SQLException {
        TargetWriteOutcome<String> outcome = access(new DataSourceTransactionManager(dataSource))
                .withWrite(session -> {
                    insert(session, 1);
                    throw new NumberFormatException(VALUE_IN_MESSAGE);
                });

        assertThat(outcome)
                .isEqualTo(new TargetWriteOutcome.Failed<>(
                        new TargetWriteFailure(TargetWriteStage.WORK, null, null, "NumberFormatException", false)));
        assertThat(outcome.toString()).doesNotContain(VALUE_IN_MESSAGE);
        assertThat(countFromAnotherConnection()).isZero();
    }
}
