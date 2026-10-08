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
package cherry.mastersmith.role.web;

import static cherry.mastersmith.role.testsupport.RoleApi.entry;
import static cherry.mastersmith.role.testsupport.RoleApi.saveBody;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.audit.testsupport.AuditRows;
import cherry.mastersmith.audit.testsupport.AuditRows.AuditRow;
import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.RowLockHolder;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleApi;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.repository.UserRepository;
import cherry.mastersmith.user.service.UserAccountService;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * ロールの操作の監査（BR11.1〜BR11.4・BR11.6・BR11.8、契約 C10、NFR5.1・NFR5.6、AC1.1.1・AC1.1.16・AC1.2.5）の結合テスト。B4 の4つの種類の
 * 成功と主な拒否の行の列を監査の表を読んで確かめ、入力の誤り・{@code ROLE_BUSY}・読み取り・木は残らないことを確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class RoleAuditIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @LocalServerPort
    int port;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    RefreshTokenRevocationService revocationService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    ActiveDslModelHolder holder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserRepository users;

    private RoleApi api;

    private RoleFixtures fixtures;

    private Actor admin;

    @BeforeEach
    void setUp() {
        api = new RoleApi(port);
        fixtures = new RoleFixtures(jdbc);
        admin = new RoleActors(userAccountService, revocationService, transactionManager, port).admin();
        RoleDslFixture.install(holder, RoleDslFixture.sample());
    }

    private AuditRows rows() {
        return new AuditRows(jdbc);
    }

    private long lastId() {
        AuditRows rows = rows();
        return rows.count() == 0 ? 0 : rows.last().auditEventId();
    }

    private AuditRow onlyRowSince(long id) {
        List<AuditRow> added =
                rows().all().stream().filter(row -> row.auditEventId() > id).toList();
        assertThat(added).hasSize(1);
        return added.getFirst();
    }

    @Test
    @DisplayName("each successful operation leaves one SUCCESS row with the actor, the role and the detail")
    void successes() {
        String name = RoleFixtures.uniqueName("監査");

        long before = lastId();
        long roleId =
                ((Number) HttpTestClient.json(api.create(admin.token(), name)).get("roleId")).longValue();
        AuditRow create = onlyRowSince(before);
        assertThat(create.eventType()).isEqualTo("ROLE_CREATED");
        assertThat(create.result()).isEqualTo("SUCCESS");
        assertThat(create.failureReason()).isNull();
        assertThat(create.actorUserId()).isEqualTo(admin.userId());
        assertThat(create.targetRoleId()).isEqualTo(roleId);
        assertThat(create.targetUserId()).isNull();
        assertThat(create.targetGroupId()).isNull();
        assertThat(create.enteredEmail()).isNull();
        assertThat(create.traceId()).isNotBlank();
        assertThat(create.detail()).isEqualTo("{\"name\":\"" + name + "\"}");

        String renamed = RoleFixtures.uniqueName("監査 改名");
        before = lastId();
        api.rename(admin.token(), roleId, renamed);
        assertThat(onlyRowSince(before).detail())
                .isEqualTo("{\"before\":\"" + name + "\",\"after\":\"" + renamed + "\"}");

        before = lastId();
        api.save(
                admin.token(),
                roleId,
                saveBody("SALES", null, List.of(entry("SALES", null, null, "READ", true, null))));
        AuditRow save = onlyRowSince(before);
        assertThat(save.eventType()).isEqualTo("ROLE_PERMISSION_CHANGED");
        assertThat(save.detail())
                .isEqualTo("{\"roleName\":\"" + renamed + "\",\"changes\":[{\"target\":{\"schemaName\":\"SALES\","
                        + "\"tableName\":null,\"columnName\":null},\"before\":{\"main\":null,\"create\":null,"
                        + "\"delete\":null},\"after\":{\"main\":\"READ\",\"create\":true,\"delete\":null}}]}");

        before = lastId();
        api.delete(admin.token(), roleId);
        AuditRow delete = onlyRowSince(before);
        assertThat(delete.eventType()).isEqualTo("ROLE_DELETED");
        assertThat(delete.targetRoleId()).as("削除の後も行は残る").isEqualTo(roleId);
        assertThat(delete.detail()).isEqualTo("{\"name\":\"" + renamed + "\"}");
    }

    @Test
    @DisplayName("each business rejection leaves one FAILURE row with its reason and detail")
    void rejections() {
        String name = RoleFixtures.uniqueName("拒否");
        long roleId = fixtures.role(name);
        fixtures.setting(roleId, PermissionTarget.schema("SALES"), MainPermission.READ);
        String sameSave = saveBody("SALES", null, List.of(entry("SALES", null, null, "READ", null, null)));
        String goneSave = saveBody("SALES", "GONE", List.of(entry("SALES", "GONE", null, "READ", null, null)));

        record Case(Runnable call, String type, String reason, Long role, String detail) {}
        List<Case> cases = List.of(
                new Case(
                        () -> api.create(admin.token(), name),
                        "ROLE_CREATED",
                        "ROLE_NAME_DUPLICATE",
                        null,
                        "{\"name\":\"" + name + "\"}"),
                new Case(
                        () -> api.rename(admin.token(), roleId, name),
                        "ROLE_RENAMED",
                        "NO_CHANGE",
                        roleId,
                        "{\"name\":\"" + name + "\"}"),
                new Case(
                        () -> api.delete(admin.token(), Long.MAX_VALUE),
                        "ROLE_DELETED",
                        "ROLE_NOT_FOUND",
                        Long.MAX_VALUE,
                        null),
                new Case(
                        () -> api.save(admin.token(), roleId, sameSave),
                        "ROLE_PERMISSION_CHANGED",
                        "NO_CHANGE",
                        roleId,
                        "{\"name\":\"" + name + "\"}"),
                new Case(
                        () -> api.save(admin.token(), roleId, goneSave),
                        "ROLE_PERMISSION_CHANGED",
                        "PERMISSION_TARGET_NOT_IN_DSL",
                        roleId,
                        "{\"name\":\"" + name + "\"}"));

        for (Case c : cases) {
            long before = lastId();
            c.call().run();
            AuditRow row = onlyRowSince(before);
            assertThat(row.eventType()).as(c.reason()).isEqualTo(c.type());
            assertThat(row.result()).isEqualTo("FAILURE");
            assertThat(row.failureReason()).isEqualTo(c.reason());
            assertThat(row.actorUserId()).isEqualTo(admin.userId());
            assertThat(row.targetRoleId()).isEqualTo(c.role());
            assertThat(row.detail()).isEqualTo(c.detail());
        }
        RoleDslFixture.remove(holder);
        long before = lastId();
        api.save(admin.token(), roleId, sameSave);
        assertThat(onlyRowSince(before).failureReason()).isEqualTo("DSL_NOT_APPLIED");
    }

    @Test
    @DisplayName("input errors, ROLE_BUSY, reads and tree reads leave no audit row")
    void notAudited() throws Exception {
        long roleId = fixtures.role(RoleFixtures.uniqueName("記録なし"));
        long before = lastId();

        api.create(admin.token(), " ");
        api.rename(admin.token(), roleId, "a".repeat(65));
        api.save(admin.token(), roleId, saveBody("SALES", null, List.of()));
        api.list(admin.token(), "");
        api.list(admin.token(), "?page=0");
        api.detail(admin.token(), roleId);
        api.detail(admin.token(), Long.MAX_VALUE);
        api.schemas(admin.token(), roleId);
        api.tables(admin.token(), Long.MAX_VALUE, "SALES");
        try (RowLockHolder held = RowLockHolder.hold(
                TestDatabase.url(tempDir), "SELECT role_id FROM roles WHERE role_id = ? FOR UPDATE", roleId)) {
            assertThat(held.lockedRows()).isEqualTo(1);
            assertThat(HttpTestClient.json(api.delete(admin.token(), roleId))).containsEntry("code", "ROLE_BUSY");
        }

        assertThat(rows().all().stream().filter(row -> row.auditEventId() > before))
                .isEmpty();
    }

    @Test
    @DisplayName("assignments, unassignments and switches leave one row each with the targets and the detail (B5)")
    void assignmentsAndSwitches() {
        String name = RoleFixtures.uniqueName("割り当ての監査");
        long roleId = fixtures.role(name);
        GroupFixtures groups = new GroupFixtures(users, jdbc);
        Actor member = new RoleActors(userAccountService, revocationService, transactionManager, port).member();
        String groupName = GroupFixtures.uniqueName("割り当ての監査");
        long groupId = groups.group(groupName);

        long before = lastId();
        api.assignUser(admin.token(), roleId, member.userId());
        AuditRow toUser = onlyRowSince(before);
        assertThat(toUser.eventType()).isEqualTo("ROLE_ASSIGNED");
        assertThat(toUser.result()).isEqualTo("SUCCESS");
        assertThat(toUser.actorUserId()).isEqualTo(admin.userId());
        assertThat(toUser.targetRoleId()).isEqualTo(roleId);
        assertThat(toUser.targetUserId()).isEqualTo(member.userId());
        assertThat(toUser.targetGroupId()).isNull();
        assertThat(toUser.detail()).isEqualTo("{\"roleName\":\"" + name + "\",\"groupName\":null}");

        before = lastId();
        api.assignGroup(admin.token(), roleId, groupId);
        AuditRow toGroup = onlyRowSince(before);
        assertThat(toGroup.targetGroupId()).isEqualTo(groupId);
        assertThat(toGroup.targetUserId()).isNull();
        assertThat(toGroup.detail()).isEqualTo("{\"roleName\":\"" + name + "\",\"groupName\":\"" + groupName + "\"}");

        before = lastId();
        api.switchWorkRole(member.token(), roleId);
        AuditRow switched = onlyRowSince(before);
        assertThat(switched.eventType()).isEqualTo("WORK_ROLE_SWITCHED");
        assertThat(switched.actorUserId()).isEqualTo(member.userId());
        assertThat(switched.targetUserId()).isEqualTo(member.userId());
        assertThat(switched.targetRoleId()).isEqualTo(roleId);
        assertThat(switched.detail())
                .isEqualTo("{\"fromRoleId\":" + roleId + ",\"fromRoleName\":\"" + name + "\",\"toRoleName\":\"" + name
                        + "\",\"storedBeforeRoleId\":null}");

        before = lastId();
        api.unassignGroup(admin.token(), roleId, groupId);
        AuditRow unassigned = onlyRowSince(before);
        assertThat(unassigned.eventType()).isEqualTo("ROLE_UNASSIGNED");
        assertThat(unassigned.result()).isEqualTo("SUCCESS");
        assertThat(unassigned.targetGroupId()).isEqualTo(groupId);
    }

    @Test
    @DisplayName("assignment and switch rejections leave one FAILURE row, reads of assignments leave none (B5)")
    void assignmentRejections() {
        String name = RoleFixtures.uniqueName("割り当ての拒否");
        long roleId = fixtures.role(name);
        Actor member = new RoleActors(userAccountService, revocationService, transactionManager, port).member();
        fixtures.assignUser(roleId, member.userId());

        long before = lastId();
        api.assignUser(admin.token(), roleId, member.userId());
        AuditRow duplicate = onlyRowSince(before);
        assertThat(duplicate.failureReason()).isEqualTo("NO_CHANGE");
        assertThat(duplicate.eventType()).isEqualTo("ROLE_ASSIGNED");

        before = lastId();
        api.assignUser(admin.token(), roleId, Long.MAX_VALUE);
        AuditRow missingUser = onlyRowSince(before);
        assertThat(missingUser.failureReason()).isEqualTo("USER_NOT_FOUND");
        assertThat(missingUser.targetUserId()).isEqualTo(Long.MAX_VALUE);

        before = lastId();
        api.assignGroup(admin.token(), roleId, Long.MAX_VALUE);
        assertThat(onlyRowSince(before).failureReason()).isEqualTo("GROUP_NOT_FOUND");

        before = lastId();
        api.switchWorkRole(member.token(), Long.MAX_VALUE);
        AuditRow notAssigned = onlyRowSince(before);
        assertThat(notAssigned.eventType()).isEqualTo("WORK_ROLE_SWITCHED");
        assertThat(notAssigned.failureReason()).isEqualTo("ROLE_NOT_ASSIGNED");
        assertThat(notAssigned.targetRoleId()).isEqualTo(Long.MAX_VALUE);
        assertThat(notAssigned.detail()).isNull();

        before = lastId();
        api.assignments(admin.token(), roleId);
        api.userRoles(admin.token(), member.userId());
        api.workRole(member.token());
        api.mySchemas(member.token());
        api.assignJson(admin.token(), roleId, "{}");
        long last = before;
        assertThat(rows().all().stream().filter(row -> row.auditEventId() > last))
                .isEmpty();
    }
}
