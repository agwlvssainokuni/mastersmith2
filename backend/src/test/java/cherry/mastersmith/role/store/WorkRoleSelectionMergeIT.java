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
package cherry.mastersmith.role.store;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.persistence.RowLockFailures;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.group.testsupport.UncommittedWrite;
import cherry.mastersmith.user.repository.UserRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 作業ロールの保存の {@code MERGE INTO … KEY(user_id)} の確かめ（NFR 設計の読み直しの R-15、計画の Step 28・D-19、
 * {@code reliability-design.md} 4.2 の #9）。
 *
 * <p>同じ利用者の保存の行を別の接続が未確定のまま持っている間に {@code MERGE} を流し、待って上限切れになるか・一意の違反（23505）に
 * なるか・ほかの誤りになるかを、SQLState と誤りの番号で確かめる。B5 の Step 28 の結果は、未確定の挿入・書き換えのどちらに対しても
 * 待った後の上限切れ（SQLState {@code HYT00}・誤りの番号 50200）で、store の区分は {@code Busy("WORK_ROLE_SELECTION_KEY")} になる。
 * そのため store の保存は {@code MERGE} のままにした（計画の D-19。{@code generation-notes.md} に記録）。このテストは H2 を上げたときに
 * この前提が崩れないかの見張りを兼ねる。合否は経過の時間ではなく、誤りの種類と行の値で決める。
 */
@SpringBootTest
class WorkRoleSelectionMergeIT {

    private static final String MERGE =
            "MERGE INTO work_role_selections (user_id, role_id, updated_at) KEY (user_id) VALUES (?, ?, ?)";

    private static final OffsetDateTime AT =
            OffsetDateTime.ofInstant(Instant.parse("2026-10-09T01:00:00Z"), ZoneOffset.UTC);

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private GroupFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new GroupFixtures(users, jdbc);
    }

    /** 別の接続で MERGE を流し、失敗したらその例外を返す（成功したら null）。 */
    private SQLException mergeInAnotherConnection(long userId, long roleId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(TestDatabase.url(tempDir), "sa", "")) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(MERGE)) {
                statement.setLong(1, userId);
                statement.setLong(2, roleId);
                statement.setObject(3, AT);
                statement.executeUpdate();
                connection.commit();
                return null;
            } catch (SQLException e) {
                connection.rollback();
                return e;
            }
        }
    }

    private static String describe(SQLException failure) {
        return "sqlState=" + failure.getSQLState() + ", errorCode=" + failure.getErrorCode() + ", lockFailure="
                + RowLockFailures.isLockFailure(failure);
    }

    @Test
    @DisplayName("MERGE while another transaction holds an uncommitted insert of the same user waits and fails")
    void mergeAgainstUncommittedInsert() throws Exception {
        long userId = fixtures.user("作業ロール 確かめ一郎");

        SQLException failure;
        try (UncommittedWrite held = UncommittedWrite.hold(
                TestDatabase.url(tempDir),
                "INSERT INTO work_role_selections (user_id, role_id, updated_at) VALUES (?, ?, ?)",
                userId,
                1L,
                AT)) {
            failure = mergeInAnotherConnection(userId, 2L);
        }

        assertThat((Object) failure).as("未確定の挿入に対する MERGE は失敗する").isNotNull();
        assertThat(RowLockFailures.isLockFailure(failure))
                .as("待ちの上限切れ（HYT00・50200）で、一意の違反やほかの誤りではない: " + describe(failure))
                .isTrue();
        assertThat(failure.getErrorCode()).as(describe(failure)).isEqualTo(50200);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM work_role_selections WHERE user_id = ?", Integer.class, userId))
                .isZero();
    }

    @Test
    @DisplayName("MERGE while another transaction holds an uncommitted update of the same user waits and fails")
    void mergeAgainstUncommittedUpdate() throws Exception {
        long userId = fixtures.user("作業ロール 確かめ二郎");
        jdbc.update("INSERT INTO work_role_selections (user_id, role_id, updated_at) VALUES (?, ?, ?)", userId, 1L, AT);

        SQLException failure;
        try (UncommittedWrite held = UncommittedWrite.hold(
                TestDatabase.url(tempDir),
                "UPDATE work_role_selections SET role_id = ? WHERE user_id = ?",
                3L,
                userId)) {
            failure = mergeInAnotherConnection(userId, 2L);
        }

        assertThat((Object) failure).as("未確定の書き換えに対する MERGE は失敗する").isNotNull();
        assertThat(RowLockFailures.isLockFailure(failure))
                .as("待ちの上限切れ（HYT00・50200）で、一意の違反やほかの誤りではない: " + describe(failure))
                .isTrue();
        assertThat(failure.getErrorCode()).as(describe(failure)).isEqualTo(50200);
        assertThat(jdbc.queryForObject(
                        "SELECT role_id FROM work_role_selections WHERE user_id = ?", Long.class, userId))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("MERGE after the other transaction committed updates the row without a failure")
    void mergeAfterCommitUpdates() throws Exception {
        long userId = fixtures.user("作業ロール 確かめ三郎");

        assertThat((Object) mergeInAnotherConnection(userId, 1L))
                .as("初めての保存は挿入")
                .isNull();
        assertThat((Object) mergeInAnotherConnection(userId, 2L))
                .as("確定の後の保存は書き換え")
                .isNull();

        assertThat(jdbc.queryForList("SELECT role_id FROM work_role_selections WHERE user_id = ?", Long.class, userId))
                .containsExactly(2L);
    }
}
