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

import ch.qos.logback.classic.Level;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.group.testsupport.UncommittedWrite;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.Role;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
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
 * ロールの排他と書き込み（{@code reliability-design.md} 2.1〜2.3・4.2 の #2・#3、BR8.3〜BR8.5、NFR3.3・NFR3.7、計画の 7.3）の結合テスト。
 *
 * <p>本物の H2 で、名前の鍵の待たない違反（先の側を確定させてから書く）と上限切れ（先の側を未確定のまま放さずに H2 の上限で切らせる）、
 * ロールの行の排他の上限切れ、ロールへの外部キー、通常の書き込みを確かめる。合否は経過の時間ではなく、結果の型・行の数・WARN で決める。
 * {@link RoleStoreOutcome.Done} 以外では、本番の {@code RoleStoreTransactions} と同じく巻き戻しの印を付ける。
 */
@SpringBootTest
class RoleStoreConstraintIT {

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
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private RoleFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new RoleFixtures(jdbc);
    }

    /** 1つ目のトランザクションで store を呼び、Done 以外なら巻き戻しの印を付ける（{@code RoleStoreTransactions} と同じ形）。 */
    private <T> RoleStoreOutcome<T> inTransaction(Function<RoleStore, RoleStoreOutcome<T>> call) {
        return tx.execute(status -> {
            RoleStoreOutcome<T> outcome = call.apply(store);
            if (!outcome.isDone()) {
                status.setRollbackOnly();
            }
            return outcome;
        });
    }

    private <T> RoleStoreOutcome<T> withLocked(long roleId, Function<Role, RoleStoreOutcome<T>> call) {
        return inTransaction(s -> switch (s.lockRole(roleId)) {
            case RoleStoreOutcome.Done<Role>(Role role) -> call.apply(role);
            default -> throw new IllegalStateException("ロールを排他できません");
        });
    }

    @Test
    @DisplayName("creating a role with a name key committed by another transaction is NameTaken without waiting (#2)")
    void nameKeyViolationWithoutWaiting() {
        String raw = RoleFixtures.uniqueName("Sales");
        fixtures.role(raw);

        RoleStoreOutcome<Role> outcome =
                inTransaction(s -> s.insertRole(RoleFixtures.name(raw.toUpperCase(Locale.ROOT)), NOW));

        assertThat(outcome).isEqualTo(new RoleStoreOutcome.NameTaken<Role>());
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM roles WHERE name_key = ?",
                        Integer.class,
                        RoleFixtures.name(raw).key()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("creating a role with a name key held uncommitted is Busy with one WARN of ROLE_NAME_KEY (#3)")
    void nameKeyWaitTimesOut() throws Exception {
        String raw = RoleFixtures.uniqueName("待ち");
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

        RoleStoreOutcome<Role> outcome;
        try (LogEvents logs = LogEvents.capture(RoleStore.class);
                UncommittedWrite held = UncommittedWrite.hold(
                        TestDatabase.url(tempDir),
                        "INSERT INTO roles (name, name_key, created_at, updated_at) VALUES (?, ?, ?, ?)",
                        raw,
                        RoleFixtures.name(raw).key(),
                        at,
                        at)) {
            outcome = inTransaction(s -> s.insertRole(RoleFixtures.name(raw), NOW));

            assertThat(logs.list()).singleElement().satisfies(warn -> {
                assertThat(warn.getLevel()).isEqualTo(Level.WARN);
                assertThat(warn.getThrowableProxy()).isNull();
                assertThat(warn.getKeyValuePairs())
                        .anySatisfy(pair -> assertThat(pair.key + "=" + pair.value)
                                .isEqualTo("lockKind=" + RoleStore.ROLE_NAME_KEY));
            });
        }

        assertThat(outcome).isEqualTo(new RoleStoreOutcome.Busy<Role>(RoleStore.ROLE_NAME_KEY));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM roles WHERE name_key = ?",
                        Integer.class,
                        RoleFixtures.name(raw).key()))
                .isZero();
    }

    @Test
    @DisplayName("locking a role row held by another transaction is Busy with one WARN of ROLE_ROW")
    void rowLockTimesOut() throws Exception {
        long roleId = fixtures.role(RoleFixtures.uniqueName("行の排他"));

        RoleStoreOutcome<Role> outcome;
        try (LogEvents logs = LogEvents.capture(RoleStore.class);
                UncommittedWrite held = UncommittedWrite.hold(
                        TestDatabase.url(tempDir),
                        "UPDATE roles SET updated_at = ? WHERE role_id = ?",
                        OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                        roleId)) {
            outcome = inTransaction(s -> s.lockRole(roleId));

            assertThat(logs.list())
                    .singleElement()
                    .satisfies(warn -> assertThat(warn.getKeyValuePairs())
                            .anySatisfy(pair -> assertThat(pair.key + "=" + pair.value)
                                    .isEqualTo("lockKind=" + RoleStore.ROLE_ROW)));
        }

        assertThat(outcome).isEqualTo(new RoleStoreOutcome.Busy<Role>(RoleStore.ROLE_ROW));
    }

    @Test
    @DisplayName("writing settings for a role deleted by a committed transaction is Referenced to the role")
    void foreignKeyToTheRole() {
        Role detached = ((RoleStoreOutcome.Done<Role>)
                        inTransaction(s -> s.insertRole(RoleFixtures.name(RoleFixtures.uniqueName("消えた")), NOW)))
                .value();
        jdbc.update("DELETE FROM roles WHERE role_id = ?", detached.getRoleId());

        RoleStoreOutcome<Void> outcome = inTransaction(s -> s.writePermissions(
                detached,
                Map.of(PermissionTarget.schema("SALES"), new PermissionValues(MainPermission.READ, null, null)),
                NOW));

        assertThat(outcome).isEqualTo(new RoleStoreOutcome.Referenced<Void>(Referent.ROLE));
        assertThat(fixtures.settingRows(detached.getRoleId())).isZero();
    }

    @Test
    @DisplayName("the write methods report Done and change the rows for the normal path")
    void normalPath() {
        assertThat(inTransaction(s -> s.lockRole(Long.MAX_VALUE))).isEqualTo(new RoleStoreOutcome.RoleMissing<Role>());
        assertThat(inTransaction(s -> s.lockRole(0L))).isEqualTo(new RoleStoreOutcome.RoleMissing<Role>());
        RoleStoreOutcome<Role> created =
                inTransaction(s -> s.insertRole(RoleFixtures.name(RoleFixtures.uniqueName("通常")), NOW));
        long roleId = ((RoleStoreOutcome.Done<Role>) created).value().getRoleId();
        PermissionTarget schema = PermissionTarget.schema("SALES");
        PermissionTarget column = PermissionTarget.column("SALES", "ORDER_LINE", "UNIT_PRICE");

        Map<PermissionTarget, PermissionValues> first = new LinkedHashMap<>();
        first.put(schema, new PermissionValues(MainPermission.READ, true, null));
        first.put(column, new PermissionValues(MainPermission.NONE, null, null));
        assertThat(withLocked(roleId, role -> store.writePermissions(role, first, NOW.plusSeconds(1))))
                .isEqualTo(new RoleStoreOutcome.Done<>(null));
        assertThat(fixtures.settingRows(roleId)).isEqualTo(2);

        Map<PermissionTarget, PermissionValues> second = new LinkedHashMap<>();
        second.put(schema, new PermissionValues(MainPermission.FULL, null, null));
        second.put(column, PermissionValues.NOT_SET);
        assertThat(withLocked(roleId, role -> store.writePermissions(role, second, NOW.plusSeconds(2)))
                        .isDone())
                .isTrue();
        assertThat(jdbc.queryForMap(
                        "SELECT main_permission, create_permission FROM permission_settings WHERE role_id = ?", roleId))
                .containsEntry("MAIN_PERMISSION", "FULL")
                .containsEntry("CREATE_PERMISSION", null);
        assertThat(((OffsetDateTime) fixtures.roleRow(roleId).get("UPDATED_AT")).toInstant())
                .isEqualTo(NOW.plusSeconds(2));

        String renamed = RoleFixtures.uniqueName("改名");
        assertThat(withLocked(roleId, role -> store.renameRole(role, RoleFixtures.name(renamed), NOW))
                        .isDone())
                .isTrue();
        assertThat(fixtures.roleRow(roleId)).containsEntry("NAME", renamed);
        assertThat(withLocked(roleId, role -> store.clearPermissions(role, List.of(column), NOW)))
                .isEqualTo(new RoleStoreOutcome.Done<>(0));
        assertThat(withLocked(roleId, role -> store.clearPermissions(role, List.of(schema), NOW)))
                .isEqualTo(new RoleStoreOutcome.Done<>(1));

        fixtures.setting(roleId, PermissionTarget.table("SALES", "ORDER_HEAD"), MainPermission.READ);
        assertThat(withLocked(roleId, store::deleteRole)).isEqualTo(new RoleStoreOutcome.Done<>(null));
        assertThat(fixtures.roleRows(roleId)).isZero();
        assertThat(fixtures.settingRows(roleId)).isZero();
    }
}
