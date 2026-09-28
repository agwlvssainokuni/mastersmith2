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
package cherry.mastersmith.invitation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * スキーマの変更 V8 と生成列 {@code pending_email} の結合テスト（BR2.5・BR11.3、NFR9.8・NFR10.1、{@code reliability-design.md} 2.1 の
 * (a)〜(d)・2.3）。
 *
 * <p>Spring を起動せず、組み込みの H2 の一時のファイルに Flyway の API で当てる。V7 までの状態は、テストの資源に置いた V1〜V7 の複写
 * （{@code db/migration-through-v7}）で作る。複写が本番の V1〜V7 と1バイトも違わないことも確かめる。
 */
class V8MigrationIT {

    /** 本番の移行の置き場。 */
    static final String CURRENT = "classpath:db/migration";

    /** V7 までの移行の複写の置き場（1つ前の版のアプリが知る移行）。 */
    static final String THROUGH_V7 = "classpath:db/migration-through-v7";

    static final String[] V1_TO_V7 = {
        "V1__u1_baseline.sql",
        "V2__u2_user_account.sql",
        "V3__u2_authentication.sql",
        "V4__u4_audit_event.sql",
        "V5__u4_dsl_management.sql",
        "V6__u4_dsl_audit_columns.sql",
        "V7__u2_user_preferences.sql"
    };

    /** 同時の追記の待ち合わせの上限（秒）。 */
    private static final long TIMEOUT_SECONDS = 20;

    @TempDir
    Path tempDir;

    static String url(Path dir) {
        return "jdbc:h2:file:" + dir.toAbsolutePath().resolve("mastersmith");
    }

    static Flyway flyway(Path dir, String location) {
        return Flyway.configure()
                .dataSource(url(dir), "sa", "")
                .locations(location)
                .validateOnMigrate(true)
                .load();
    }

    static JdbcTemplate jdbc(Path dir) {
        return new JdbcTemplate(new DriverManagerDataSource(url(dir), "sa", ""));
    }

    /** 招待を1行追記する（トークンのハッシュは連番から作る）。 */
    static void insert(JdbcTemplate jdbc, long adminId, String email, String state, int seq) {
        jdbc.update(
                "INSERT INTO invitations (email, language, token_hash, invited_by_user_id, invited_at, expires_at,"
                        + " send_result, state) VALUES (?, 'ja', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'PENDING', ?)",
                email,
                hash(seq),
                adminId,
                state);
    }

    static byte[] hash(int seq) {
        return HexFormat.of().parseHex("%064x".formatted(seq));
    }

    static long admin(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO users (email, password_hash, admin_flag, created_at, display_name)"
                + " VALUES ('admin@example.com', '$2a$04$h', TRUE, CURRENT_TIMESTAMP, '管理者')");
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email = 'admin@example.com'", Long.class);
    }

    private static byte[] resource(String path) {
        try (InputStream in = V8MigrationIT.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("資源 %s が無い", path).isNotNull();
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sqlState(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    @Test
    @DisplayName("the copies of V1 to V7 in the test resources are byte-for-byte identical to the production files")
    void copiesAreIdentical() {
        for (String file : V1_TO_V7) {
            assertThat(resource("db/migration-through-v7/" + file))
                    .as(file)
                    .isEqualTo(resource("db/migration/" + file));
        }
        assertThat(flyway(tempDir, THROUGH_V7).info().all()).hasSize(V1_TO_V7.length);
    }

    @Test
    @DisplayName("V8 is applied once on top of V7 and keeps the existing rows")
    void appliedOnceOnTopOfV7() {
        flyway(tempDir, THROUGH_V7).migrate();
        JdbcTemplate jdbc = jdbc(tempDir);
        admin(jdbc);

        MigrateResult result = flyway(tempDir, CURRENT).migrate();
        MigrateResult again = flyway(tempDir, CURRENT).migrate();

        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(result.targetSchemaVersion).isEqualTo("8");
        assertThat(again.migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invitations", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("(a) a second PENDING row for the same email violates the unique constraint (23505)")
    void secondPendingIsRejected() {
        flyway(tempDir, CURRENT).migrate();
        JdbcTemplate jdbc = jdbc(tempDir);
        long adminId = admin(jdbc);
        insert(jdbc, adminId, "hanako@example.com", "PENDING", 1);

        assertThatThrownBy(() -> insert(jdbc, adminId, "hanako@example.com", "PENDING", 2))
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23505"))
                .hasMessageContaining("UK_INVITATIONS_PENDING_EMAIL");
        assertThat(jdbc.queryForObject("SELECT pending_email FROM invitations WHERE state = 'PENDING'", String.class))
                .isEqualTo("hanako@example.com");
    }

    @Test
    @DisplayName("(b) rows that are not PENDING may share the email and have no pending email")
    void endedRowsMayRepeat() {
        flyway(tempDir, CURRENT).migrate();
        JdbcTemplate jdbc = jdbc(tempDir);
        long adminId = admin(jdbc);

        insert(jdbc, adminId, "hanako@example.com", "CANCELLED", 1);
        insert(jdbc, adminId, "hanako@example.com", "REPLACED", 2);
        insert(jdbc, adminId, "hanako@example.com", "COMPLETED", 3);
        insert(jdbc, adminId, "hanako@example.com", "PENDING", 4);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invitations WHERE pending_email IS NULL", Integer.class))
                .isEqualTo(3);
        assertThatThrownBy(() -> insert(jdbc, adminId, "other@example.com", "EXPIRED", 5))
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23513"));
    }

    @Test
    @DisplayName("(c) after the PENDING row is cancelled a new PENDING row can be created")
    void cancelFreesTheEmail() {
        flyway(tempDir, CURRENT).migrate();
        JdbcTemplate jdbc = jdbc(tempDir);
        long adminId = admin(jdbc);
        insert(jdbc, adminId, "hanako@example.com", "PENDING", 1);

        jdbc.update("UPDATE invitations SET state = 'CANCELLED', ended_at = CURRENT_TIMESTAMP");
        insert(jdbc, adminId, "hanako@example.com", "PENDING", 2);

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM invitations WHERE pending_email = 'hanako@example.com'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("(d) a concurrent uncommitted insert waits for the first commit and then violates the constraint")
    void concurrentInsertWaitsThenFails() throws Exception {
        flyway(tempDir, CURRENT).migrate();
        long adminId = admin(jdbc(tempDir));
        String sql = "INSERT INTO invitations (email, language, token_hash, invited_by_user_id, invited_at, expires_at,"
                + " send_result, state) VALUES ('hanako@example.com', 'ja', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,"
                + " 'PENDING', 'PENDING')";
        try (Connection first = DriverManager.getConnection(url(tempDir), "sa", "");
                Connection second = DriverManager.getConnection(url(tempDir), "sa", "")) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            try (var statement = second.createStatement()) {
                // 既定の待ちの上限（1 秒）より長くし、待ちに入ったことを確かめてから1つ目を確定する。
                statement.execute("SET LOCK_TIMEOUT " + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            }
            try (PreparedStatement statement = first.prepareStatement(sql)) {
                statement.setBytes(1, hash(1));
                statement.setLong(2, adminId);
                statement.executeUpdate();
            }
            CompletableFuture<String> outcome = CompletableFuture.supplyAsync(() -> {
                try (PreparedStatement statement = second.prepareStatement(sql)) {
                    statement.setBytes(1, hash(2));
                    statement.setLong(2, adminId);
                    statement.executeUpdate();
                    second.commit();
                    return "inserted";
                } catch (SQLException e) {
                    return e.getSQLState();
                }
            });
            awaitBlocked(outcome);

            first.commit();

            assertThat(outcome.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("23505");
        }
        assertThat(jdbc(tempDir)
                        .queryForObject("SELECT COUNT(*) FROM invitations WHERE state = 'PENDING'", Integer.class))
                .isEqualTo(1);
    }

    /**
     * 2つ目の追記が1つ目の確定を待っている（H2 のセッションの一覧で、2つ目の追記の文が実行中のままである）ことを上限の時間つきで
     * 待つ。1つ目は確定していないため、2つ目の文が実行中である間は1つ目の確定を待っている。実時刻の sleep に頼らない。
     */
    private void awaitBlocked(CompletableFuture<String> outcome) throws SQLException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        try (Connection observer = DriverManager.getConnection(url(tempDir), "sa", "");
                PreparedStatement blocked = observer.prepareStatement("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS"
                        + " WHERE EXECUTING_STATEMENT LIKE 'INSERT INTO invitations%'")) {
            while (System.nanoTime() < deadline) {
                assertThat(outcome).as("2つ目の追記が待たずに終わった").isNotDone();
                try (var rows = blocked.executeQuery()) {
                    rows.next();
                    if (rows.getInt(1) > 0) {
                        return;
                    }
                }
                Thread.onSpinWait();
            }
        }
        throw new AssertionError("2つ目の追記が待ちに入らなかった");
    }
}
