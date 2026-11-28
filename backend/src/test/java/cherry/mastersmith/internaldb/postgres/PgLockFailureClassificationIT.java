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
import cherry.mastersmith.dslmanage.domain.DslSource;
import cherry.mastersmith.dslmanage.repository.DslPreviewRepository;
import cherry.mastersmith.internaldb.testsupport.ConflictProbe;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgApp;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import cherry.mastersmith.role.store.RoleStore;
import cherry.mastersmith.role.store.RoleStoreOutcome;
import jakarta.persistence.PersistenceException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 待ちの上限切れ（SQLState {@code 55P03}）の分類（BR5.3）。(1) 実際の PostgreSQL が投げる生の {@code SQLException}（{@code KeyedUpsertExecutor} が
 * JDBC で受ける形）と、それを JPA の例外で包んだ形の両方が {@code RowLockFailures} に当たる。(2) アプリの経路では、作業ロールの保存は
 * {@code Busy}、プレビューを置くは排他の失敗として分類できる例外になる。(3) {@code KeyedUpsertExecutor} の中で行き詰まり
 * （{@code 40P01}）が起きて、アプリの側が犠牲になったとき、作業ロールの保存が想定外でなく排他の失敗（{@code Busy}）に分類される。
 */
@InternalDbPostgresIT
class PgLockFailureClassificationIT {

    private static final String UPDATE_SELECTION =
            "UPDATE work_role_selections SET role_id = ?, updated_at = ? WHERE user_id = ?";

    private static PgApp app;

    private static PortableFixture fixture;

    private static TransactionTemplate tx;

    private ConflictProbe probe;

    @BeforeAll
    static void start() {
        app = PgApp.start();
        fixture = new PortableFixture(app);
        tx = new TransactionTemplate(app.context().getBean(PlatformTransactionManager.class));
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

    private PortableFixture.Account memberWithSelection(long[] roles) {
        PortableFixture.Account member = fixture.createUser(false);
        roles[0] = fixture.createRole(PortableFixture.uniqueName("cls"));
        roles[1] = fixture.createRole(PortableFixture.uniqueName("cls"));
        fixture.executeSql("INSERT INTO work_role_selections (user_id, role_id, updated_at) VALUES (" + member.userId()
                + ", " + roles[0] + ", now())");
        return member;
    }

    /** 保持側とは別の接続で、アプリと同じ待ちの上限（2 秒）の中で更新の文を流し、待ちの上限切れの生の例外を返す。 */
    private static SQLException rawUpdateFailure(long userId, long roleId) throws SQLException {
        try (Connection raw = app.database().connect()) {
            raw.setAutoCommit(false);
            try (Statement statement = raw.createStatement()) {
                statement.execute("SET lock_timeout = '2s'");
            }
            try (PreparedStatement update = raw.prepareStatement(UPDATE_SELECTION)) {
                update.setLong(1, roleId);
                update.setObject(2, java.time.OffsetDateTime.now());
                update.setLong(3, userId);
                update.executeUpdate();
                return null;
            } catch (SQLException e) {
                return e;
            } finally {
                raw.rollback();
            }
        }
    }

    @Test
    @DisplayName("a raw 55P03 SQLException from PostgreSQL, and the same wrapped in a JPA exception, are lock failures")
    void rawAndWrappedAreLockFailures() throws Exception {
        long[] roles = new long[2];
        PortableFixture.Account member = memberWithSelection(roles);
        Connection holder = probe.openHolder();
        ConflictProbe.execute(
                holder, "UPDATE work_role_selections SET updated_at = updated_at WHERE user_id = " + member.userId());

        SQLException raw = rawUpdateFailure(member.userId(), roles[1]);

        assertThat((Object) raw).as("the wait is cut by lock_timeout").isNotNull();
        assertThat(raw.getSQLState()).isEqualTo("55P03");
        assertThat(RowLockFailures.isLockFailure(raw)).as("raw").isTrue();
        assertThat(RowLockFailures.isLockFailure(new PersistenceException(raw)))
                .as("wrapped")
                .isTrue();
        assertThat(RowLockFailures.isLockFailure(new RuntimeException(new PersistenceException(raw))))
                .as("wrapped twice")
                .isTrue();
    }

    @Test
    @DisplayName("through the application path, a work role save past the wait limit is Busy on the selection key")
    void workRoleSaveIsBusy() throws Exception {
        long[] roles = new long[2];
        PortableFixture.Account member = memberWithSelection(roles);
        RoleStore store = app.context().getBean(RoleStore.class);
        Connection holder = probe.openHolder();
        ConflictProbe.execute(
                holder, "UPDATE work_role_selections SET updated_at = updated_at WHERE user_id = " + member.userId());

        RoleStoreOutcome<Void> outcome = tx.execute(status -> {
            RoleStoreOutcome<Void> saved = store.saveWorkRoleSelection(member.userId(), roles[1], Instant.now());
            status.setRollbackOnly();
            return saved;
        });

        assertThat(outcome).isEqualTo(new RoleStoreOutcome.Busy<>(RoleStore.WORK_ROLE_SELECTION_KEY));
    }

    @Test
    @DisplayName(
            "through the application path, a preview place past the wait limit throws an exception classified as a lock failure")
    void previewPlaceIsALockFailure() throws Exception {
        fixture.executeSql("DELETE FROM dsl_previews");
        fixture.placePreview(PortableFixture.minimalDsl("cls_t"));
        DslPreviewRepository previews = app.context().getBean(DslPreviewRepository.class);
        long adminId = fixture.adminUserId();
        Connection holder = probe.openHolder();
        ConflictProbe.execute(holder, "UPDATE dsl_previews SET dsl_hash = dsl_hash WHERE preview_slot = 1");

        Throwable thrown = null;
        try {
            tx.execute(status -> previews.place(
                    UUID.randomUUID(),
                    "version: 2\n".getBytes(StandardCharsets.UTF_8),
                    "f".repeat(64),
                    DslSource.PASTE,
                    adminId,
                    Instant.now()));
        } catch (RuntimeException e) {
            thrown = e;
        }

        assertThat(thrown).isNotNull();
        assertThat(RowLockFailures.isLockFailure(thrown))
                .as("the exception class is %s", thrown.getClass().getSimpleName())
                .isTrue();
    }

    @Test
    @DisplayName(
            "a deadlock detected inside the keyed upsert is classified as a lock failure (Busy), not as an unexpected failure")
    void deadlockInsideTheKeyedUpsertIsBusy() throws Exception {
        long[] roles = new long[2];
        PortableFixture.Account member = memberWithSelection(roles);
        long other = fixture.createRole(PortableFixture.uniqueName("cls"));
        RoleStore store = app.context().getBean(RoleStore.class);
        JdbcTemplate jdbc = app.context().getBean(JdbcTemplate.class);
        // 犠牲になる側は、先に待ち始めた側の deadlock_timeout（1 秒）が輪の閉じた後に最初に切れる側で、観測のたびに入れ替わりうる。アプリが犠牲になるまで
        // （最大 5 回）やり直す（保持側が犠牲になった回は、アプリ側の保存が成功する）。
        String appResult = "";
        for (int attempt = 1; attempt <= 5; attempt++) {
            Connection holder = probe.openHolder();
            ConflictProbe.execute(
                    holder,
                    "UPDATE work_role_selections SET updated_at = updated_at WHERE user_id = " + member.userId());
            Future<String> observing = probe.async(() -> {
                try {
                    return tx.execute(status -> {
                        jdbc.update("UPDATE roles SET updated_at = updated_at WHERE role_id = ?", other);
                        RoleStoreOutcome<Void> outcome =
                                store.saveWorkRoleSelection(member.userId(), roles[1], Instant.now());
                        status.setRollbackOnly();
                        return "outcome=" + outcome.getClass().getSimpleName();
                    });
                } catch (RuntimeException e) {
                    return "thrown=" + e.getClass().getSimpleName() + " lockFailure="
                            + RowLockFailures.isLockFailure(e);
                }
            });
            probe.awaitWaiting();
            String holderState = ConflictProbe.tryExecute(
                    holder, "UPDATE roles SET updated_at = updated_at WHERE role_id = " + other);
            appResult = observing.get(15, TimeUnit.SECONDS);
            probe.release(holder);
            System.out.println("INTERNAL_DB_PG_OBSERVE keyedUpsertDeadlock attempt=" + attempt + " app{" + appResult
                    + "} holderSqlState=" + holderState);
            probe.awaitNoWaiters();
            if (!appResult.equals("outcome=Done")) {
                break;
            }
        }
        assertThat(appResult)
                .as("the application side must be the deadlock victim within 5 attempts, and is a lock failure")
                .isEqualTo("outcome=Busy");
    }
}
