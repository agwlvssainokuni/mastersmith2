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

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.RoleRejection;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
 * 確かめの後の変更の結合テスト（{@code reliability-design.md} 4.2 の #13、NFR3.1 (f)・NFR3.8、BR9.10・BR9.13、AC3.1.4・AC3.1.5）。
 *
 * <p>確かめ → 別の要求（本番の業務処理）で保存・削除・同名の作成、DSL の差し替え・外し → 適用の順に送る。適用は
 * {@code ROLE_TRANSFER_STALE}（DSL を外したときは {@code DSL_NOT_APPLIED}）で断られ、状態は別の要求の後のままで、監査に FAILURE が
 * 1 行残る。DSL の差し替えと外しは、DSL の管理の適用と同じ提供口（{@link ActiveDslModelHolder}）を置き換えて起こす。
 */
@SpringBootTest
class RoleTransferStaleIT {

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.71", "Mozilla/5.0", "trace-0071");

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    RoleTransferService service;

    @Autowired
    RoleAdminService admin;

    @Autowired
    ActiveDslModelHolder holder;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private RoleFixtures roles;

    private long actor;

    private String name;

    private long roleId;

    private String yaml;

    @BeforeEach
    void setUp() {
        roles = new RoleFixtures(jdbc);
        actor = new GroupFixtures(users, jdbc).user("確かめの後 確かめ一郎");
        RoleDslFixture.install(holder, RoleDslFixture.sample());
        name = RoleFixtures.uniqueName("確かめの後");
        roleId = roles.role(name);
        roles.setting(roleId, PermissionTarget.schema("SALES"), MainPermission.READ);
        yaml = "version: 1\nroles:\n  \"" + name
                + "\":\n    schemas:\n      SALES: {main: FULL}\n      HR: {main: READ}\n";
    }

    private static RoleTransferBody body(String yaml) {
        return new RoleTransferBody(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    private String check(String yaml) {
        return ((TransferCheckResult.Checked) service.check(body(yaml))).plan().fingerprint();
    }

    private int failures(String reason) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type = 'ROLE_TRANSFER_APPLIED' AND result = 'FAILURE'"
                        + " AND failure_reason = ?",
                Integer.class,
                reason);
    }

    private List<Map<String, Object>> settingsOf(long id) {
        return jdbc.queryForList(
                "SELECT schema_name, main_permission FROM permission_settings WHERE role_id = ? ORDER BY schema_name",
                id);
    }

    @Test
    @DisplayName("a permission save by another request after the check makes the apply stale and keeps the saved value")
    void savedAfterCheck() {
        String fingerprint = check(yaml);
        assertThat(admin.savePermissions(
                        actor,
                        ORIGIN,
                        roleId,
                        new PermissionInputs.SaveCommand(
                                PermissionTarget.schema("SALES"),
                                Map.of(
                                        PermissionTarget.schema("SALES"),
                                        new PermissionValues(MainPermission.NONE, null, null)))))
                .isInstanceOf(RoleChangeResult.Done.class);
        int before = failures("ROLE_TRANSFER_STALE");

        assertThat(service.apply(actor, ORIGIN, body(yaml), fingerprint))
                .isEqualTo(new TransferApplyResult.Rejected(RoleRejection.TRANSFER_STALE));
        assertThat(settingsOf(roleId)).containsExactly(Map.of("SCHEMA_NAME", "SALES", "MAIN_PERMISSION", "NONE"));
        assertThat(failures("ROLE_TRANSFER_STALE")).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("a deletion of the replaced role after the check makes the apply stale and the role stays deleted")
    void deletedAfterCheck() {
        String fingerprint = check(yaml);
        assertThat(admin.delete(actor, ORIGIN, roleId)).isInstanceOf(RoleChangeResult.Done.class);

        assertThat(service.apply(actor, ORIGIN, body(yaml), fingerprint))
                .isEqualTo(new TransferApplyResult.Rejected(RoleRejection.TRANSFER_STALE));
        assertThat(roles.roleRows(roleId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE name = ?", Integer.class, name))
                .isZero();
    }

    @Test
    @DisplayName("a role with the same name created after the check makes the apply of a new role stale")
    void sameNameCreatedAfterCheck() {
        String created = RoleFixtures.uniqueName("同名");
        String newRole = "version: 1\nroles:\n  \"" + created + "\":\n    schemas:\n      HR: {main: READ}\n";
        String fingerprint = check(newRole);
        assertThat(admin.create(actor, ORIGIN, created.toUpperCase(java.util.Locale.ROOT)))
                .isInstanceOf(RoleCreateResult.Created.class);

        assertThat(service.apply(actor, ORIGIN, body(newRole), fingerprint))
                .isEqualTo(new TransferApplyResult.Rejected(RoleRejection.TRANSFER_STALE));
        long createdId = jdbc.queryForObject(
                "SELECT role_id FROM roles WHERE name = ?", Long.class, created.toUpperCase(java.util.Locale.ROOT));
        assertThat(roles.settingRows(createdId)).isZero();
    }

    @Test
    @DisplayName("a newly applied DSL after the check makes the apply stale")
    void dslReappliedAfterCheck() {
        String fingerprint = check(yaml);
        DslModel sample = RoleDslFixture.sample();
        RoleDslFixture.install(holder, new DslModel("f".repeat(64), 2, sample.schemas(), List.of()));

        assertThat(service.apply(actor, ORIGIN, body(yaml), fingerprint))
                .isEqualTo(new TransferApplyResult.Rejected(RoleRejection.TRANSFER_STALE));
        assertThat(settingsOf(roleId)).containsExactly(Map.of("SCHEMA_NAME", "SALES", "MAIN_PERMISSION", "READ"));
    }

    @Test
    @DisplayName("a removed DSL after the check is DSL_NOT_APPLIED rather than stale, with a failure audit")
    void dslRemovedAfterCheck() {
        String fingerprint = check(yaml);
        RoleDslFixture.remove(holder);
        int before = failures("DSL_NOT_APPLIED");

        assertThat(service.apply(actor, ORIGIN, body(yaml), fingerprint))
                .isEqualTo(new TransferApplyResult.Rejected(RoleRejection.DSL_NOT_APPLIED));
        assertThat(service.check(body(yaml))).isInstanceOf(TransferCheckResult.DslNotApplied.class);
        assertThat(settingsOf(roleId)).containsExactly(Map.of("SCHEMA_NAME", "SALES", "MAIN_PERMISSION", "READ"));
        assertThat(failures("DSL_NOT_APPLIED")).isEqualTo(before + 1);
    }
}
