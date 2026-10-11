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

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.Role;
import cherry.mastersmith.role.domain.RoleRejection;
import cherry.mastersmith.role.service.RoleFirstStep;
import cherry.mastersmith.role.service.RoleStoreTransactions;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
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

/**
 * 権限の YAML の適用の JDBC のバッチの結合テスト（{@code reliability-design.md} 2.5、{@code performance-design.md} 2.3、BR9.6・BR9.7・
 * BR9.11、NFR3.7 の U4、計画の 9.3）。
 *
 * <p>本物の H2 で、本番と同じ {@link RoleStoreTransactions} を通して store の適用を呼ぶ。置き換えるロールは本番と同じく先に行を排他する。
 */
@SpringBootTest
class RoleStoreTransferBatchIT {

    private static final Instant NOW = Instant.parse("2026-10-09T05:00:00Z");

    private static final PermissionValues READ = new PermissionValues(MainPermission.READ, null, null);

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    RoleStoreTransactions transactions;

    @Autowired
    RoleStore roleStore;

    @Autowired
    JdbcTemplate jdbc;

    private RoleFixtures roles;

    @BeforeEach
    void setUp() {
        roles = new RoleFixtures(jdbc);
    }

    private static Map<PermissionTarget, PermissionValues> columns(int count) {
        return columns("S", count);
    }

    private static Map<PermissionTarget, PermissionValues> columns(String schema, int count) {
        Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
        settings.put(PermissionTarget.schema(schema), new PermissionValues(MainPermission.NONE, true, false));
        for (int i = 0; i < count; i++) {
            settings.put(PermissionTarget.column(schema, "T" + (i / 100), "C" + i), READ);
        }
        return settings;
    }

    /** ロールを排他してから適用を書き、成功なら確定、そうでなければ巻き戻す（本番の入口を通す）。 */
    private RoleStoreOutcome<?> apply(Function<RoleStore, List<TransferWrite>> writes, boolean rollbackAfterWrite) {
        RoleFirstStep<TransferWritten> step = transactions.inFirst(store -> {
            RoleStoreOutcome<TransferWritten> outcome = store.applyTransfer(writes.apply(store), NOW);
            if (!(outcome instanceof RoleStoreOutcome.Done<TransferWritten>(TransferWritten written))) {
                return new RoleFirstStep.Store<>(outcome, null);
            }
            return rollbackAfterWrite
                    ? new RoleFirstStep.Rejected<>(RoleRejection.TRANSFER_STALE, null)
                    : new RoleFirstStep.Done<>(written);
        });
        return switch (step) {
            case RoleFirstStep.Done<TransferWritten>(TransferWritten written) -> new RoleStoreOutcome.Done<>(written);
            case RoleFirstStep.Store<TransferWritten>(RoleStoreOutcome<?> outcome, var detail) -> outcome;
            case RoleFirstStep.Rejected<TransferWritten> rejected -> null;
        };
    }

    private static Role locked(RoleStore store, long roleId) {
        return ((RoleStoreOutcome.Done<Role>) store.lockRole(roleId)).value();
    }

    private int rowsOfName(String name) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE name = ?", Integer.class, name);
    }

    @Test
    @DisplayName(
            "settings are written in batches of 1,000 and the replaced role loses its old rows and gets a new time")
    void batchesAndReplacement() {
        long replacedId = roles.role(RoleFixtures.uniqueName("置き換え"));
        roles.setting(replacedId, PermissionTarget.schema("OLD"), MainPermission.FULL);
        roles.setting(replacedId, PermissionTarget.column("OLD", "T", "C"), MainPermission.READ);
        String createdName = RoleFixtures.uniqueName("作る");

        RoleStoreOutcome<?> outcome = apply(
                store -> List.of(
                        TransferWrite.replace(locked(store, replacedId), Map.of(PermissionTarget.schema("NEW"), READ)),
                        TransferWrite.create(RoleFixtures.name(createdName), columns(2_499))),
                false);

        assertThat(outcome).isEqualTo(new RoleStoreOutcome.Done<>(new TransferWritten(1, 1, 2_501, 3)));
        assertThat(jdbc.queryForList(
                        "SELECT schema_name FROM permission_settings WHERE role_id = ?", String.class, replacedId))
                .containsExactly("NEW");
        long createdId = jdbc.queryForObject("SELECT role_id FROM roles WHERE name = ?", Long.class, createdName);
        assertThat(roles.settingRows(createdId)).isEqualTo(2_500);
        assertThat(jdbc.queryForObject(
                        "SELECT create_permission FROM permission_settings WHERE role_id = ? AND table_name = ''",
                        Boolean.class,
                        createdId))
                .isTrue();
        assertThat(jdbc.queryForObject(
                                "SELECT updated_at FROM roles WHERE role_id = ?", OffsetDateTime.class, replacedId)
                        .toInstant())
                .isEqualTo(NOW);
    }

    @Test
    @DisplayName("creating a role whose name key is taken is NameTaken and nothing is written")
    void nameTaken() {
        String name = RoleFixtures.uniqueName("重なり");
        long existing = roles.role(name);
        long replacedId = roles.role(RoleFixtures.uniqueName("置き換え"));
        roles.setting(replacedId, PermissionTarget.schema("KEEP"), MainPermission.READ);

        RoleStoreOutcome<?> outcome = apply(
                store -> List.of(
                        TransferWrite.replace(locked(store, replacedId), Map.of(PermissionTarget.schema("NEW"), READ)),
                        TransferWrite.create(RoleFixtures.name(name.toUpperCase(java.util.Locale.ROOT)), columns(1))),
                false);

        assertThat(outcome).isEqualTo(new RoleStoreOutcome.NameTaken<>());
        assertThat(rowsOfName(name)).isEqualTo(1);
        assertThat(roles.settingRows(existing)).isZero();
        assertThat(jdbc.queryForList(
                        "SELECT schema_name FROM permission_settings WHERE role_id = ?", String.class, replacedId))
                .containsExactly("KEEP");
    }

    @Test
    @DisplayName("the JDBC batch and the Hibernate insert are rolled back together with the transaction")
    void rolledBackTogether() {
        long replacedId = roles.role(RoleFixtures.uniqueName("置き換え"));
        roles.setting(replacedId, PermissionTarget.schema("KEEP"), MainPermission.READ);
        String createdName = RoleFixtures.uniqueName("作る");

        RoleStoreOutcome<?> outcome = apply(
                store -> List.of(
                        TransferWrite.replace(locked(store, replacedId), Map.of(PermissionTarget.schema("NEW"), READ)),
                        TransferWrite.create(RoleFixtures.name(createdName), columns("ROLLBACK", 1_500))),
                true);

        assertThat(outcome).isNull();
        assertThat(rowsOfName(createdName)).isZero();
        assertThat(jdbc.queryForList(
                        "SELECT schema_name FROM permission_settings WHERE role_id = ?", String.class, replacedId))
                .containsExactly("KEEP");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM permission_settings WHERE schema_name = 'ROLLBACK'", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("the statements have a 10 second limit, the batch size is 1,000 and an empty write writes nothing")
    void limitsAndEmpty() {
        assertThat(roleStore.transferQueryTimeoutSeconds()).isEqualTo(10);
        assertThat(RoleStore.TRANSFER_BATCH_SIZE).isEqualTo(1_000);
        assertThat(apply(store -> List.of(), false))
                .isEqualTo(new RoleStoreOutcome.Done<>(new TransferWritten(0, 0, 0, 0)));
        assertThat(TransferWrite.create(RoleFixtures.name("x"), Map.of()).toString())
                .isEqualTo("TransferWrite[CREATE, settings=0]");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TransferWrite(null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> TransferWrite.create(
                        RoleFixtures.name("x"), Map.of(PermissionTarget.schema("S"), PermissionValues.NOT_SET)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
