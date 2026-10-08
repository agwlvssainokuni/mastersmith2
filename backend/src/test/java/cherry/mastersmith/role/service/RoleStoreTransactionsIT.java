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
package cherry.mastersmith.role.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.role.domain.Role;
import cherry.mastersmith.role.domain.RoleAuditDetail;
import cherry.mastersmith.role.domain.RoleAuditEvent;
import cherry.mastersmith.role.domain.RoleAuditFailure;
import cherry.mastersmith.role.domain.RoleOperation;
import cherry.mastersmith.role.domain.RoleRejection;
import cherry.mastersmith.role.store.RoleStore;
import cherry.mastersmith.role.store.RoleStoreOutcome;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.domain.RequestOrigin;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * store を呼ぶ1つ目のトランザクションの入口（{@code reliability-design.md} 2.4、NFR3.4、計画の D-4・R-14 の後半）の結合テスト。
 *
 * <p>本物の H2 で一意の違反を起こし、{@link RoleStoreTransactions} を通すと1つ目が例外なく巻き戻り、続く2つ目のトランザクションで出した
 * 失敗の出来事が確定して監査の行になることを確かめる。比べとして、巻き戻しの印を付けずに戻すと確定のときに
 * {@link UnexpectedRollbackException} になることも確かめる。
 */
@SpringBootTest
class RoleStoreTransactionsIT {

    private static final Instant NOW = Instant.parse("2026-10-08T11:00:00Z");

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.11", null, "trace-role-tx");

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    RoleStoreTransactions transactions;

    @Autowired
    RoleStore store;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    JdbcTemplate jdbc;

    private RoleFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new RoleFixtures(jdbc);
    }

    private int auditRows(String eventType) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type = ? AND trace_id = 'trace-role-tx'",
                Integer.class,
                eventType);
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName(
            "a violation in the first transaction rolls back quietly and the failure event commits in a second one")
    void violationThenFailureEvent() {
        String raw = RoleFixtures.uniqueName("T5");
        fixtures.role(raw);

        RoleFirstStep<Role> step = transactions.inFirst(s -> switch (s.insertRole(RoleFixtures.name(raw), NOW)) {
            case RoleStoreOutcome.Done<Role>(Role role) -> new RoleFirstStep.Done<>(role);
            case RoleStoreOutcome<Role> other -> new RoleFirstStep.Store<>(other, new RoleAuditDetail.Name(raw));
        });
        tx.executeWithoutResult(status -> publisher.publishEvent(RoleAuditEvent.failed(
                RoleOperation.CREATE,
                1L,
                null,
                RoleAuditFailure.ROLE_NAME_DUPLICATE,
                new RoleAuditDetail.Name(raw),
                NOW,
                ORIGIN)));

        assertThat(step).isInstanceOf(RoleFirstStep.Store.class);
        assertThat(((RoleFirstStep.Store<Role>) step).outcome()).isEqualTo(new RoleStoreOutcome.NameTaken<>());
        assertThat(step.toString()).isEqualTo("Store[NameTaken]");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM roles WHERE name_key = ?",
                        Integer.class,
                        RoleFixtures.name(raw).key()))
                .isEqualTo(1);
        assertThat(auditRows("ROLE_CREATED")).isEqualTo(1);
    }

    @Test
    @DisplayName("a rejection before the write is rolled back including what was written, and a success commits")
    void rejectionAndSuccess() {
        String raw = RoleFixtures.uniqueName("T5 成功");

        RoleFirstStep<Role> rejected = transactions.inFirst(s -> {
            s.insertRole(RoleFixtures.name(raw), NOW);
            return new RoleFirstStep.Rejected<>(RoleRejection.NO_CHANGE, null);
        });
        assertThat(rejected).isInstanceOf(RoleFirstStep.Rejected.class);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM roles WHERE name_key = ?",
                        Integer.class,
                        RoleFixtures.name(raw).key()))
                .as("Done 以外は書いたものも巻き戻る")
                .isZero();

        RoleFirstStep<Role> done = transactions.inFirst(s -> switch (s.insertRole(RoleFixtures.name(raw), NOW)) {
            case RoleStoreOutcome.Done<Role>(Role role) -> new RoleFirstStep.Done<>(role);
            case RoleStoreOutcome<Role> other -> new RoleFirstStep.Store<>(other, null);
        });
        assertThat(done).isInstanceOf(RoleFirstStep.Done.class);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM roles WHERE name_key = ?",
                        Integer.class,
                        RoleFixtures.name(raw).key()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("without the rollback mark the same violation fails at commit with UnexpectedRollbackException")
    void withoutMarkTheCommitFails() {
        String raw = RoleFixtures.uniqueName("T5 印なし");
        fixtures.role(raw);

        assertThatThrownBy(() -> tx.execute(status -> store.insertRole(RoleFixtures.name(raw), NOW)))
                .isInstanceOf(UnexpectedRollbackException.class);
    }
}
