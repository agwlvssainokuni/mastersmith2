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
package cherry.mastersmith.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.group.domain.Group;
import cherry.mastersmith.group.domain.GroupName;
import cherry.mastersmith.group.domain.GroupNameValidation;
import cherry.mastersmith.group.store.GroupStore;
import cherry.mastersmith.group.store.StoreOutcome;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.user.repository.UserRepository;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * store を呼ぶ1つ目のトランザクションの入口（{@code reliability-design.md} 1.2 の T5・2.3、NFR3.4、計画の D-5）の結合テスト。
 *
 * <p>本物の H2 で一意の違反を起こし、{@link GroupStoreTransactions} を通すと1つ目が例外なく巻き戻り、続く2つ目のトランザクションが
 * 確定することを確かめる。比べとして、巻き戻しの印を付けずに戻すと確定のときに {@link UnexpectedRollbackException} になること
 * （捨ての試しの T5 の形）も確かめる。
 */
@SpringBootTest
class GroupStoreTransactionsIT {

    private static final Instant NOW = Instant.parse("2026-10-08T11:00:00Z");

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    GroupStoreTransactions transactions;

    @Autowired
    GroupStore store;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private GroupFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new GroupFixtures(users, jdbc);
    }

    private static GroupName name(String raw) {
        return ((GroupNameValidation.Valid) GroupName.parse(raw)).name();
    }

    @Test
    @DisplayName(
            "a violation in the first transaction rolls back without an exception and a second transaction commits")
    void violationRollsBackQuietly() {
        String raw = GroupFixtures.uniqueName("T5");
        fixtures.group(raw);

        FirstStep<Group> step = transactions.inFirst(s -> switch (s.insertGroup(name(raw), NOW)) {
            case StoreOutcome.Done<Group>(Group group) -> new FirstStep.Done<>(group);
            case StoreOutcome<Group> other -> new FirstStep.Store<>(other, null);
        });
        String second = GroupFixtures.uniqueName("T5 2つ目");
        Long created = tx.execute(status -> fixtures.group(second));

        assertThat(step).isEqualTo(new FirstStep.Store<Group>(new StoreOutcome.NameTaken<>(), null));
        assertThat(created).isPositive();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM groups WHERE name_key = ?",
                        Integer.class,
                        name(raw).key()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a rejection before the write is rolled back, and a success commits")
    void rejectionAndSuccess() {
        String raw = GroupFixtures.uniqueName("T5 成功");

        FirstStep<Group> rejected = transactions.inFirst(s -> {
            s.insertGroup(name(raw), NOW);
            return new FirstStep.Rejected<>(cherry.mastersmith.group.domain.GroupRejection.NO_CHANGE, null);
        });
        assertThat(rejected).isInstanceOf(FirstStep.Rejected.class);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM groups WHERE name_key = ?",
                        Integer.class,
                        name(raw).key()))
                .as("Done 以外は書いたものも巻き戻る")
                .isZero();

        FirstStep<Group> done = transactions.inFirst(s -> switch (s.insertGroup(name(raw), NOW)) {
            case StoreOutcome.Done<Group>(Group group) -> new FirstStep.Done<>(group);
            case StoreOutcome<Group> other -> new FirstStep.Store<>(other, null);
        });
        assertThat(done).isInstanceOf(FirstStep.Done.class);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM groups WHERE name_key = ?",
                        Integer.class,
                        name(raw).key()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("without the rollback mark the same violation fails at commit with UnexpectedRollbackException")
    void withoutMarkTheCommitFails() {
        String raw = GroupFixtures.uniqueName("T5 印なし");
        fixtures.group(raw);

        assertThatThrownBy(() -> tx.execute(status -> store.insertGroup(name(raw), NOW)))
                .isInstanceOf(UnexpectedRollbackException.class);
    }
}
