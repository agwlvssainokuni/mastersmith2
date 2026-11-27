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

import cherry.mastersmith.common.testsupport.StatementCounter;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.repository.UserRepository;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 作業ロールの保存の文の数の確かめ（Intent 261114-internal-db-postgresql の U2、NFR1.2・NFR7。計画の Step 7）。
 *
 * <p>行が既にあるときは更新の 1 文、行が無い最初の 1 回は更新と挿入の 2 文で、どちらも接続は同じトランザクションの 1 本だけ。存在しない
 * 利用者への外部キーの違反が、これまでと同じ区分（{@link RoleStoreOutcome.Referenced}）で届くことも確かめる（R-02 (2)）。
 */
@SpringBootTest
@Import(StatementCounter.Config.class)
class WorkRoleSelectionStatementCountIT {

    private static final Instant NOW = Instant.parse("2026-10-08T07:00:00Z");

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    RoleStore store;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    StatementCounter counter;

    private RoleFixtures roles;

    private GroupFixtures groups;

    @BeforeEach
    void setUp() {
        roles = new RoleFixtures(jdbc);
        groups = new GroupFixtures(users, jdbc);
    }

    private RoleStoreOutcome<Void> save(long userId, long roleId, Instant at) {
        return tx.execute(status -> {
            RoleStoreOutcome<Void> outcome = store.saveWorkRoleSelection(userId, roleId, at);
            if (!outcome.isDone()) {
                status.setRollbackOnly();
            }
            return outcome;
        });
    }

    @Test
    @DisplayName("the first save runs an update and an insert and a later save runs a single update on one connection")
    void statementCounts() {
        long first = roles.role(RoleFixtures.uniqueName("文の数の一"));
        long second = roles.role(RoleFixtures.uniqueName("文の数の二"));
        long userId = groups.user("文の数 確かめ一郎");

        counter.reset();
        assertThat(save(userId, first, NOW)).isEqualTo(new RoleStoreOutcome.Done<>(null));
        assertThat(counter.statements()).as("first save statements").isEqualTo(2);
        assertThat(counter.connections()).as("first save connections").isEqualTo(1);

        counter.reset();
        assertThat(save(userId, second, NOW.plusSeconds(5))).isEqualTo(new RoleStoreOutcome.Done<>(null));
        assertThat(counter.statements()).as("later save statements").isEqualTo(1);
        assertThat(counter.connections()).as("later save connections").isEqualTo(1);

        assertThat(jdbc.queryForObject(
                        "SELECT role_id FROM work_role_selections WHERE user_id = ?", Long.class, userId))
                .isEqualTo(second);
    }

    @Test
    @DisplayName("a missing user is reported as Referenced to the user and no row is written")
    void foreignKeyViolationIsReferenced() {
        long roleId = roles.role(RoleFixtures.uniqueName("文の数の外部キー"));

        assertThat(save(Long.MAX_VALUE, roleId, NOW)).isEqualTo(new RoleStoreOutcome.Referenced<>(Referent.USER));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM work_role_selections WHERE user_id = ?", Integer.class, Long.MAX_VALUE))
                .isZero();
    }
}
