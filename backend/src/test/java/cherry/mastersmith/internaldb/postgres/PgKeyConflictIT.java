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
package cherry.mastersmith.internaldb.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.persistence.RowLockFailures;
import cherry.mastersmith.dslmanage.domain.DslPreviewRef;
import cherry.mastersmith.dslmanage.domain.DslSource;
import cherry.mastersmith.dslmanage.repository.DslPreviewRepository;
import cherry.mastersmith.internaldb.testsupport.ConflictProbe;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgApp;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import cherry.mastersmith.role.store.RoleStore;
import cherry.mastersmith.role.store.RoleStoreOutcome;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 鍵の重なり（{@code KeyedUpsertExecutor.isKeyConflict}）の 3 つを、実際の PostgreSQL の例外の文で確かめる（BR5.2。U2 のレビューから持ち越し）。
 * (a) 作業ロールの主キーと (b) プレビューの主キーは、先の側が確定していない同じ鍵を持つ間に挿入して待ち、先の側が確定すると文の照合
 * （小文字の制約名を大文字にして照合）が重なりに当たり、更新をやり直して後の値が残る。(c) {@code preview_id} の一意の制約は重なりと扱わず、失敗として出る。
 * 3 つとも別のテストメソッドで、重なりの後に同じトランザクションが続けられる（25P02 にならない）ことも確かめる。
 */
@InternalDbPostgresIT
class PgKeyConflictIT {

    private static final String SLOT_CHECK = "ck_dsl_previews_slot";

    private static PgApp app;

    private static PortableFixture fixture;

    private static TransactionTemplate tx;

    private static JdbcTemplate jdbc;

    private ConflictProbe probe;

    @BeforeAll
    static void start() {
        app = PgApp.start();
        fixture = new PortableFixture(app);
        tx = new TransactionTemplate(app.context().getBean(PlatformTransactionManager.class));
        jdbc = app.context().getBean(JdbcTemplate.class);
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    @BeforeEach
    void setUp() {
        probe = new ConflictProbe(app.database());
    }

    @AfterEach
    void tearDown() {
        try {
            probe.releaseHolders();
            probe.awaitNoWaiters();
        } finally {
            probe.close();
        }
    }

    /** 保存の結果と、同じトランザクションの中で読んだ値と、続けた 2 回目の保存の結果。 */
    private record Continued(
            RoleStoreOutcome<Void> outcome, long roleInTransaction, RoleStoreOutcome<Void> second, int selectOne) {}

    private static String insertPreviewSql(int slot, UUID previewId, long placedBy) {
        return "INSERT INTO dsl_previews (preview_slot, preview_id, yaml_bytes, dsl_hash, source, placed_by_user_id,"
                + " placed_at) VALUES (" + slot + ", '" + previewId + "', decode('76', 'hex'), '" + "d".repeat(64)
                + "', 'UPLOAD', " + placedBy + ", now())";
    }

    private static int place(DslPreviewRepository previews, UUID previewId, String hash, long placedBy) {
        return previews.place(
                previewId,
                "version: 2\n".getBytes(StandardCharsets.UTF_8),
                hash,
                DslSource.PASTE,
                placedBy,
                Instant.now());
    }

    @Test
    @DisplayName("(a) a work role primary key conflict converges to the later value and the transaction goes on")
    void workRoleSelectionPrimaryKey(CapturedOutput output) throws Exception {
        int logStart = output.getAll().length();
        PortableFixture.Account member = fixture.createUser(false);
        long earlier = fixture.createRole(PortableFixture.uniqueName("key"));
        long later = fixture.createRole(PortableFixture.uniqueName("key"));
        RoleStore store = app.context().getBean(RoleStore.class);
        Connection holder = probe.openHolder();
        ConflictProbe.execute(
                holder,
                "INSERT INTO work_role_selections (user_id, role_id, updated_at) VALUES (" + member.userId() + ", "
                        + earlier + ", now())");

        Future<Continued> saving = probe.async(() -> tx.execute(status -> {
            RoleStoreOutcome<Void> outcome = store.saveWorkRoleSelection(member.userId(), later, Instant.now());
            long inTransaction = jdbc.queryForObject(
                    "SELECT role_id FROM work_role_selections WHERE user_id = ?", Long.class, member.userId());
            RoleStoreOutcome<Void> second = store.saveWorkRoleSelection(member.userId(), later, Instant.now());
            int one = jdbc.queryForObject("SELECT 1", Integer.class);
            return new Continued(outcome, inTransaction, second, one);
        }));
        probe.awaitWaiting();
        holder.commit();
        Continued result = saving.get(15, TimeUnit.SECONDS);
        probe.release(holder);

        assertThat(result.outcome()).isEqualTo(new RoleStoreOutcome.Done<>(null));
        assertThat(result.roleInTransaction())
                .as("the later value is written after the conflict")
                .isEqualTo(later);
        assertThat(result.second()).isEqualTo(new RoleStoreOutcome.Done<>(null));
        assertThat(result.selectOne()).isOne();
        assertThat(fixture.queryLong("SELECT role_id FROM work_role_selections WHERE user_id = " + member.userId()))
                .isEqualTo(later);
        assertThat(output.getAll().substring(logStart))
                .as("the user's email address is not in the application log")
                .doesNotContain(member.email());
    }

    @Test
    @DisplayName("(b) a preview primary key conflict converges to the later preview and the transaction goes on")
    void previewPrimaryKey() throws Exception {
        DslPreviewRepository previews = app.context().getBean(DslPreviewRepository.class);
        long adminId = fixture.adminUserId();
        fixture.executeSql("DELETE FROM dsl_previews");
        UUID earlier = UUID.randomUUID();
        UUID later = UUID.randomUUID();
        Connection holder = probe.openHolder();
        ConflictProbe.execute(holder, insertPreviewSql(1, earlier, adminId));

        Future<Integer> placing = probe.async(() -> tx.execute(status -> {
            int written = place(previews, later, "e".repeat(64), adminId);
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            DslPreviewRef inTransaction = previews.findRef().orElseThrow();
            return one == 1 && later.equals(inTransaction.previewId()) ? written : -1;
        }));
        probe.awaitWaiting();
        holder.commit();
        int written = placing.get(15, TimeUnit.SECONDS);
        probe.release(holder);

        assertThat(written).isEqualTo(1);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM dsl_previews")).isOne();
        DslPreviewRef ref = tx.execute(status -> previews.findRef()).orElseThrow();
        assertThat(ref.previewId()).as("the later preview remains").isEqualTo(later);
        assertThat(ref.dslHash()).isEqualTo("e".repeat(64));
    }

    @Test
    @DisplayName(
            "(c) a preview_id unique violation is not treated as a conflict and surfaces as a failure the transaction survives")
    void previewIdUniqueIsNotAConflict() {
        DslPreviewRepository previews = app.context().getBean(DslPreviewRepository.class);
        long adminId = fixture.adminUserId();
        fixture.executeSql("DELETE FROM dsl_previews");
        // 1 つの表に 1 行しか置けない検査（preview_slot = 1）を専用のデータベースの中で一時的に外し、別の行（スロット 2）に preview_id を持たせる。
        fixture.executeSql("ALTER TABLE dsl_previews DROP CONSTRAINT " + SLOT_CHECK);
        try {
            UUID taken = UUID.randomUUID();
            fixture.executeSql(insertPreviewSql(2, taken, adminId));

            Throwable thrown = tx.execute(status -> {
                Throwable caught = null;
                try {
                    place(previews, taken, "f".repeat(64), adminId);
                } catch (RuntimeException e) {
                    caught = e;
                }
                Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
                assertThat(one)
                        .as("the transaction survives the savepoint rollback")
                        .isOne();
                status.setRollbackOnly();
                return caught;
            });

            assertThat(thrown).as("the violation is thrown, not converged").isNotNull();
            assertThat(sqlStateOf(thrown)).isEqualTo("23505");
            assertThat(RowLockFailures.isLockFailure(thrown)).isFalse();
            assertThat(fixture.queryLong("SELECT COUNT(*) FROM dsl_previews WHERE preview_slot = 1"))
                    .isZero();
            assertThat(fixture.queryLong("SELECT COUNT(*) FROM dsl_previews WHERE preview_slot = 2"))
                    .isOne();
        } finally {
            fixture.executeSql("DELETE FROM dsl_previews");
            fixture.executeSql("ALTER TABLE dsl_previews ADD CONSTRAINT " + SLOT_CHECK + " CHECK (preview_slot = 1)");
        }
    }

    private static String sqlStateOf(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
