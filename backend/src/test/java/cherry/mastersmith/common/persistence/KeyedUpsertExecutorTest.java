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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.PersistenceException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 「あれば更新・無ければ挿入」の手順を、本物の H2 の接続で確かめる（JDBC の動作は模擬しない）。 */
class KeyedUpsertExecutorTest {

    /** 行の値に見立てた、出力の中で見分けやすい文字。 */
    private static final String SECRET = "row-value-7f3a@example.com";

    private static final OffsetDateTime AT = OffsetDateTime.of(2026, 11, 20, 9, 0, 0, 0, ZoneOffset.UTC);

    private String url;

    private Connection main;

    private Connection other;

    @BeforeEach
    void setUp() throws SQLException {
        url = "jdbc:h2:mem:upsert-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=600";
        main = DriverManager.getConnection(url, "sa", "");
        other = DriverManager.getConnection(url, "sa", "");
        try (Statement statement = main.createStatement()) {
            statement.execute("CREATE TABLE users (user_id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE work_role_selections (user_id BIGINT NOT NULL, role_id BIGINT NOT NULL,"
                    + " updated_at TIMESTAMP WITH TIME ZONE NOT NULL,"
                    + " CONSTRAINT pk_work_role_selections PRIMARY KEY (user_id),"
                    + " CONSTRAINT fk_work_role_selections_user FOREIGN KEY (user_id) REFERENCES users (user_id))");
            // 本物の表と違い、スロットの CHECK 制約は置かない（preview_id の一意の制約の違反を作るため）。
            statement.execute("CREATE TABLE dsl_previews (preview_slot INTEGER PRIMARY KEY, preview_id UUID NOT NULL,"
                    + " yaml_bytes BINARY LARGE OBJECT NOT NULL, dsl_hash VARCHAR(64) NOT NULL,"
                    + " source VARCHAR(16) NOT NULL, placed_by_user_id BIGINT NOT NULL,"
                    + " placed_at TIMESTAMP WITH TIME ZONE NOT NULL,"
                    + " CONSTRAINT uk_dsl_previews_preview_id UNIQUE (preview_id))");
            statement.execute("INSERT INTO users (user_id) VALUES (1), (2)");
        }
        main.setAutoCommit(false);
        other.setAutoCommit(true);
    }

    @AfterEach
    void tearDown() throws SQLException {
        main.rollback();
        main.close();
        other.close();
    }

    private static Object[] updateArgs(long roleId, long userId) {
        return new Object[] {roleId, AT, userId};
    }

    private static Object[] insertArgs(long roleId, long userId) {
        return new Object[] {userId, roleId, AT};
    }

    private long roleOf(long userId) throws SQLException {
        try (Statement statement = other.createStatement();
                ResultSet rs =
                        statement.executeQuery("SELECT role_id FROM work_role_selections WHERE user_id = " + userId)) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }

    private void exec(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Test
    @DisplayName("an existing row is updated with a single statement and reported as UPDATED")
    void existingRowIsUpdated() throws SQLException {
        exec(
                other,
                "INSERT INTO work_role_selections VALUES (1, 10, TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00')");

        UpsertOutcome outcome = KeyedUpsertExecutor.execute(
                main, KeyedUpsert.WORK_ROLE_SELECTIONS, updateArgs(20, 1), insertArgs(20, 1));
        main.commit();

        assertThat(outcome).isEqualTo(UpsertOutcome.UPDATED);
        assertThat(roleOf(1)).isEqualTo(20);
    }

    @Test
    @DisplayName("a missing row is inserted and reported as INSERTED")
    void missingRowIsInserted() throws SQLException {
        UpsertOutcome outcome = KeyedUpsertExecutor.execute(
                main, KeyedUpsert.WORK_ROLE_SELECTIONS, updateArgs(30, 2), insertArgs(30, 2));
        main.commit();

        assertThat(outcome).isEqualTo(UpsertOutcome.INSERTED);
        assertThat(roleOf(2)).isEqualTo(30);
    }

    @Test
    @DisplayName("a key conflict on insert falls back to updating the row that appeared meanwhile")
    void conflictFallsBackToUpdate() throws SQLException {
        // 更新が 0 行になった後・挿入の前に、別の接続が同じ鍵の行を確定させる。
        Connection hooked = hook(
                main,
                "setSavepoint",
                () -> exec(
                        other,
                        "INSERT INTO work_role_selections VALUES (1, 10, TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00')"));

        UpsertOutcome outcome = KeyedUpsertExecutor.execute(
                hooked, KeyedUpsert.WORK_ROLE_SELECTIONS, updateArgs(40, 1), insertArgs(40, 1));
        main.commit();

        assertThat(outcome).isEqualTo(UpsertOutcome.UPDATED_AFTER_CONFLICT);
        assertThat(roleOf(1)).isEqualTo(40);
    }

    @Test
    @DisplayName("when the row disappears again after the conflict the failure is KeyedUpsertLostException")
    void lostRowAfterConflictIsReported() throws SQLException {
        Connection hooked = hook(
                main,
                "setSavepoint",
                () -> exec(
                        other,
                        "INSERT INTO work_role_selections VALUES (1, 10, TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00')"));
        hooked = hook(hooked, "rollback", () -> exec(other, "DELETE FROM work_role_selections WHERE user_id = 1"));

        Connection target = hooked;
        assertThatThrownBy(() -> KeyedUpsertExecutor.execute(
                        target, KeyedUpsert.WORK_ROLE_SELECTIONS, updateArgs(50, 1), insertArgs(50, 1)))
                .isInstanceOf(KeyedUpsertLostException.class)
                .isInstanceOf(PersistenceException.class)
                .hasNoCause();
    }

    @Test
    @DisplayName("a failure that is not a key conflict is rethrown as is after rolling back to the savepoint")
    void otherFailureIsRethrownAndTransactionStaysUsable() throws SQLException {
        exec(
                main,
                "INSERT INTO work_role_selections VALUES (2, 11, TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00')");

        // 存在しない利用者（外部キーの違反）。
        assertThatThrownBy(() -> KeyedUpsertExecutor.execute(
                        main, KeyedUpsert.WORK_ROLE_SELECTIONS, updateArgs(60, 999), insertArgs(60, 999)))
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isNotEqualTo("23505"));

        // 同じトランザクションで先に書いた行は残り、次の文も通る。
        UpsertOutcome next = KeyedUpsertExecutor.execute(
                main, KeyedUpsert.WORK_ROLE_SELECTIONS, updateArgs(61, 2), insertArgs(61, 2));
        main.commit();
        assertThat(next).isEqualTo(UpsertOutcome.UPDATED);
        assertThat(roleOf(2)).isEqualTo(61);
    }

    @Test
    @DisplayName("a unique violation on preview_id of dsl_previews is not treated as a key conflict")
    void previewIdViolationIsNotAKeyConflict() throws SQLException {
        UUID shared = UUID.randomUUID();
        exec(
                other,
                "INSERT INTO dsl_previews VALUES (2, '" + shared + "', X'01', 'h', 'PASTE', 1,"
                        + " TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00')");
        Object[] arguments = {shared, new byte[] {1, 2}, "hash", "UPLOAD", 1L, AT};

        assertThatThrownBy(() -> KeyedUpsertExecutor.execute(main, KeyedUpsert.DSL_PREVIEWS, arguments, arguments))
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23505"));
    }

    @Test
    @DisplayName("the preview row is inserted and then replaced with the bytes written as given")
    void previewInsertThenReplace() throws SQLException {
        byte[] first = {1, 2, 3};
        byte[] second = {9, 8, 7, 6};
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Object[] one = {firstId, first, "h1", "UPLOAD", 1L, AT};
        Object[] two = {secondId, second, "h2", "PASTE", 2L, AT};

        assertThat(KeyedUpsertExecutor.execute(main, KeyedUpsert.DSL_PREVIEWS, one, one))
                .isEqualTo(UpsertOutcome.INSERTED);
        assertThat(KeyedUpsertExecutor.execute(main, KeyedUpsert.DSL_PREVIEWS, two, two))
                .isEqualTo(UpsertOutcome.UPDATED);
        main.commit();

        try (Statement statement = other.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT preview_id, yaml_bytes, dsl_hash, source, placed_by_user_id FROM dsl_previews")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getObject(1, UUID.class)).isEqualTo(secondId);
            assertThat(rs.getBytes(2)).isEqualTo(second);
            assertThat(rs.getString(3)).isEqualTo("h2");
            assertThat(rs.getString(4)).isEqualTo("PASTE");
            assertThat(rs.getLong(5)).isEqualTo(2L);
            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    @DisplayName("an update that hits the lock timeout is thrown as is and is not treated as a key conflict")
    void updateLockTimeoutIsNotAConflict() throws SQLException {
        exec(
                other,
                "INSERT INTO work_role_selections VALUES (1, 10, TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00')");
        other.setAutoCommit(false);
        exec(other, "UPDATE work_role_selections SET role_id = 11 WHERE user_id = 1");
        try {
            long started = System.nanoTime();
            assertThatThrownBy(() -> KeyedUpsertExecutor.execute(
                            main, KeyedUpsert.WORK_ROLE_SELECTIONS, updateArgs(70, 1), insertArgs(70, 1)))
                    .isInstanceOfSatisfying(
                            SQLException.class,
                            e -> assertThat(RowLockFailures.isLockFailure(e)).isTrue());
            // 待ちの上限（接続の URL の値）に近い時間をかけて切れたこと（挿入や更新のやり直しに進んでいない）。
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            assertThat(elapsedMillis).isLessThan(5_000);
        } finally {
            other.rollback();
            other.setAutoCommit(true);
        }
    }

    static Stream<Arguments> conflictTable() {
        String h2Pk = "Unique index or primary key violation: \"PRIMARY KEY ON PUBLIC.WORK_ROLE_SELECTIONS(USER_ID)"
                + " [1]\" [23505-240]";
        String pgPk = "ERROR: duplicate key value violates unique constraint \"pk_work_role_selections\"";
        String pgPkDetail = pgPk + "\n  Detail: Key (user_id)=(" + SECRET + ") already exists.";
        String h2Preview = "Unique index or primary key violation: \"PUBLIC.PRIMARY_KEY_3 ON PUBLIC.DSL_PREVIEWS"
                + "(PREVIEW_SLOT) VALUES ( /* 1 */ 1)\"";
        String h2PreviewId = "Unique index or primary key violation: \"PUBLIC.UK_DSL_PREVIEWS_PREVIEW_ID_INDEX_3 ON"
                + " PUBLIC.DSL_PREVIEWS(PREVIEW_ID NULLS FIRST)\"";
        String pgPreviewPk = "ERROR: duplicate key value violates unique constraint \"dsl_previews_pkey\"";
        String pgPreviewId = "ERROR: duplicate key value violates unique constraint \"uk_dsl_previews_preview_id\"";
        return Stream.of(
                Arguments.of(
                        "H2 primary key of work role selections",
                        KeyedUpsert.WORK_ROLE_SELECTIONS,
                        "23505",
                        h2Pk,
                        true),
                Arguments.of(
                        "PostgreSQL lower case constraint name only",
                        KeyedUpsert.WORK_ROLE_SELECTIONS,
                        "23505",
                        pgPk,
                        true),
                Arguments.of(
                        "PostgreSQL message with Detail holding a value",
                        KeyedUpsert.WORK_ROLE_SELECTIONS,
                        "23505",
                        pgPkDetail,
                        true),
                Arguments.of("H2 slot key of dsl_previews", KeyedUpsert.DSL_PREVIEWS, "23505", h2Preview, true),
                Arguments.of("PostgreSQL dsl_previews_pkey", KeyedUpsert.DSL_PREVIEWS, "23505", pgPreviewPk, true),
                Arguments.of(
                        "H2 preview_id unique index is not a conflict",
                        KeyedUpsert.DSL_PREVIEWS,
                        "23505",
                        h2PreviewId,
                        false),
                Arguments.of(
                        "PostgreSQL uk_dsl_previews_preview_id is not a conflict",
                        KeyedUpsert.DSL_PREVIEWS,
                        "23505",
                        pgPreviewId,
                        false),
                Arguments.of(
                        "another SQLState with the table name", KeyedUpsert.WORK_ROLE_SELECTIONS, "23503", h2Pk, false),
                Arguments.of(
                        "no table name in the message",
                        KeyedUpsert.WORK_ROLE_SELECTIONS,
                        "23505",
                        "duplicate key",
                        false),
                Arguments.of("the other table's name", KeyedUpsert.WORK_ROLE_SELECTIONS, "23505", pgPreviewPk, false),
                Arguments.of("null message", KeyedUpsert.WORK_ROLE_SELECTIONS, "23505", null, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("conflictTable")
    @DisplayName("isKeyConflict classifies H2 and PostgreSQL shaped failures")
    void isKeyConflictTable(String label, KeyedUpsert upsert, String sqlState, String message, boolean expected) {
        assertThat(KeyedUpsertExecutor.isKeyConflict(upsert, new SQLException(message, sqlState)))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("isKeyConflict looks through the cause chain up to ten levels and ignores null")
    void isKeyConflictWalksTheChain() {
        SQLException conflict = new SQLException("dup WORK_ROLE_SELECTIONS", "23505");
        Throwable wrapped = new PersistenceException("outer", new RuntimeException("mid", conflict));
        assertThat(KeyedUpsertExecutor.isKeyConflict(KeyedUpsert.WORK_ROLE_SELECTIONS, wrapped))
                .isTrue();

        Throwable deep = conflict;
        for (int i = 0; i < 10; i++) {
            deep = new RuntimeException("level " + i, deep);
        }
        assertThat(KeyedUpsertExecutor.isKeyConflict(KeyedUpsert.WORK_ROLE_SELECTIONS, deep))
                .isFalse();
        assertThat(KeyedUpsertExecutor.isKeyConflict(KeyedUpsert.WORK_ROLE_SELECTIONS, null))
                .isFalse();
    }

    @Test
    @DisplayName("isKeyConflict only returns a boolean and a lost-row exception carries no values")
    void noValueIsExposed() {
        SQLException withValue =
                new SQLException("duplicate key WORK_ROLE_SELECTIONS Key (user_id)=(" + SECRET + ")", "23505");
        assertThat(KeyedUpsertExecutor.isKeyConflict(KeyedUpsert.WORK_ROLE_SELECTIONS, withValue))
                .isTrue();

        KeyedUpsertLostException lost = new KeyedUpsertLostException(KeyedUpsert.DSL_PREVIEWS);
        assertThat(lost.getMessage()).contains("DSL_PREVIEWS").doesNotContain(SECRET);
        assertThat(lost.getCause()).isNull();
        assertThat(lost).isInstanceOf(PersistenceException.class);
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }

    /** 指定の名前のメソッドを呼んだ後に、動作を差し込む接続の包み（本物の接続へそのまま渡す）。 */
    private static Connection hook(Connection delegate, String methodName, SqlAction after) {
        return (Connection) Proxy.newProxyInstance(
                KeyedUpsertExecutorTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                    if (method.getName().equals(methodName)
                            && (args == null || args.length == 0 || args[0] instanceof Savepoint)) {
                        after.run();
                    }
                    return result;
                });
    }
}
