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

import cherry.mastersmith.auth.testsupport.H2SessionWaits;
import cherry.mastersmith.auth.testsupport.SqlStatementCounter;
import cherry.mastersmith.common.persistence.RowLockAttempt;
import cherry.mastersmith.common.testsupport.RowLockHolder;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Preferences;
import cherry.mastersmith.user.domain.Theme;
import cherry.mastersmith.user.domain.User;
import cherry.mastersmith.user.service.UserAccountService;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 利用者の行の排他の結合テスト（Intent 260930-user-admin の U3、BR3.1・BR3.3・BR3.5、NFR4.3・NFR4.5、{@code reliability-design.md}
 * 1.4・2.1・4節の本番版）。
 *
 * <p>内部DB はクラスごとの一時ディレクトリの H2 で、テストの間でデータを持ち越すため、各テストは自分で作った利用者の ID だけで確かめる。
 * 排他の上限切れは、別の接続で行を持ち続ける {@link RowLockHolder} で起こす。
 */
@SpringBootTest(
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "cherry.mastersmith.auth.testsupport.SqlStatementCounter")
class UserRowLockRepositoryIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    UserRowLockRepository rowLocks;

    @Autowired
    UserRepository users;

    @Autowired
    UserAccountService userAccounts;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private static final String HOLD_USER = "SELECT user_id FROM users WHERE user_id = ? FOR UPDATE";

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private long create(boolean admin, Instant createdAt) {
        String email = "rowlock-" + UUID.randomUUID() + "@example.com";
        return users.save(new User(
                        email,
                        "$2a$04$hash",
                        admin,
                        createdAt,
                        new Preferences("行の排他 試験", Language.JA, Theme.SYSTEM, FontSize.MD)))
                .getUserId();
    }

    private RowLockAttempt<List<Long>> lockAdminRows(long target) {
        return tx.execute(status -> rowLocks.lockAdminRowsAndTarget(target));
    }

    /** 別の接続から、上限 50 ミリ秒で行の排他を試し、取れたかを返す（取れたらすぐ放す）。 */
    private static boolean lockableNow(long userId) throws SQLException {
        try (Connection probe = DriverManager.getConnection(TestDatabase.url(tempDir), "sa", "")) {
            probe.setAutoCommit(false);
            try (Statement statement = probe.createStatement()) {
                statement.execute("SET LOCK_TIMEOUT 50");
            }
            try (PreparedStatement select = probe.prepareStatement(HOLD_USER)) {
                select.setLong(1, userId);
                try (ResultSet rows = select.executeQuery()) {
                    rows.next();
                }
                return true;
            } catch (SQLException e) {
                if (e.getErrorCode() == 50200) {
                    return false;
                }
                throw e;
            } finally {
                probe.rollback();
            }
        }
    }

    @Test
    @DisplayName("admin rows and the target row are locked one by one in ascending user id order")
    void locksInAscendingIdOrder() throws Exception {
        // 入れた順（ID の順）と登録した日時の向きを逆にする。
        long admin1 = create(true, BASE.plusSeconds(40));
        long target = create(false, BASE.plusSeconds(30));
        long admin2 = create(true, BASE.plusSeconds(20));
        long admin3 = create(true, BASE.plusSeconds(10));
        long other = create(false, BASE);

        Future<RowLockAttempt<List<Long>>> locking;
        try (RowLockHolder holder = RowLockHolder.hold(TestDatabase.url(tempDir), HOLD_USER, admin2)) {
            assertThat(holder.lockedRows()).isEqualTo(1);
            locking = executor.submit(() -> lockAdminRows(target));
            H2SessionWaits.awaitExecuting(
                    TestDatabase.url(tempDir), "%from users%for update%", Duration.ofSeconds(2), locking::isDone);

            // admin2 で待っている間、それより小さい ID の行は排他され、大きい ID の行はまだ排他されていない。
            assertThat(lockableNow(admin1)).isFalse();
            assertThat(lockableNow(target)).isFalse();
            assertThat(lockableNow(admin3)).isTrue();
            assertThat(lockableNow(other)).isTrue();
        }
        RowLockAttempt<List<Long>> attempt = locking.get(10, TimeUnit.SECONDS);

        assertThat(attempt).isInstanceOf(RowLockAttempt.Acquired.class);
        List<Long> locked = ((RowLockAttempt.Acquired<List<Long>>) attempt).value();
        assertThat(locked).isSorted().contains(admin1, target, admin2, admin3).doesNotContain(other);
    }

    @Test
    @DisplayName("suspended admins are locked too, and admin rows are locked even when the target does not exist")
    void suspendedAdminsAndMissingTarget() {
        long suspendedAdmin = create(true, BASE);
        long admin = create(true, BASE);
        long nonAdmin = create(false, BASE);
        tx.executeWithoutResult(status -> userAccounts.setSuspended(suspendedAdmin, true));

        RowLockAttempt<List<Long>> attempt = lockAdminRows(Long.MAX_VALUE);

        assertThat(attempt).isInstanceOf(RowLockAttempt.Acquired.class);
        assertThat(((RowLockAttempt.Acquired<List<Long>>) attempt).value())
                .contains(suspendedAdmin, admin)
                .doesNotContain(nonAdmin, Long.MAX_VALUE);
    }

    @Test
    @DisplayName("a row held by another connection makes the admin rows lock return Busy after at least 3000 ms")
    void adminRowsBusy() throws Exception {
        long target = create(false, BASE);
        try (RowLockHolder holder = RowLockHolder.hold(TestDatabase.url(tempDir), HOLD_USER, target)) {
            long start = System.nanoTime();
            RowLockAttempt<List<Long>> attempt = lockAdminRows(target);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(attempt).isInstanceOf(RowLockAttempt.Busy.class);
            assertThat(elapsedMillis).isBetween(3000L, 9000L);
        }
    }

    @Test
    @DisplayName("lockUserRow locks only the target row and reports whether the user exists")
    void userRowOnly() throws Exception {
        long admin = create(true, BASE);
        long target = create(false, BASE);
        try (RowLockHolder holder = RowLockHolder.hold(TestDatabase.url(tempDir), HOLD_USER, admin)) {
            long start = System.nanoTime();
            RowLockAttempt<Boolean> attempt = tx.execute(status -> rowLocks.lockUserRow(target));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(attempt).isEqualTo(RowLockAttempt.acquired(true));
            assertThat(elapsedMillis).isLessThan(2000L);
        }
        RowLockAttempt<Boolean> missing = tx.execute(status -> rowLocks.lockUserRow(Long.MAX_VALUE));
        assertThat(missing).isEqualTo(RowLockAttempt.acquired(false));
        try (RowLockHolder holder = RowLockHolder.hold(TestDatabase.url(tempDir), HOLD_USER, target)) {
            long start = System.nanoTime();
            RowLockAttempt<Boolean> attempt = tx.execute(status -> rowLocks.lockUserRow(target));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(attempt).isInstanceOf(RowLockAttempt.Busy.class);
            assertThat(elapsedMillis).isBetween(3000L, 9000L);
        }
    }

    @Test
    @DisplayName("the admin rows lock scans the primary key in order (EXPLAIN shows PRIMARY_KEY and index sorted)")
    void explainUsesPrimaryKey() {
        long target = create(false, BASE);
        SqlStatementCounter.start();
        lockAdminRows(target);
        SqlStatementCounter.stop();
        List<String> lockSql = SqlStatementCounter.recorded().stream()
                .map(SqlStatementCounter.Recorded::sql)
                .filter(sql -> sql.toLowerCase(Locale.ROOT).contains("for update"))
                .toList();
        assertThat(lockSql).hasSize(1);
        String sql = lockSql.getFirst();
        assertThat(sql.toLowerCase(Locale.ROOT)).contains("order by").contains("for update wait 3");

        List<Map<String, Object>> plan = jdbc.queryForList("EXPLAIN " + sql, target);

        String text = plan.toString();
        assertThat(text).contains("PRIMARY_KEY").contains("index sorted");
    }
}
