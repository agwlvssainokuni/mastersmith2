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
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.repository.UserRepository;
import java.nio.file.Path;
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
 * ロールの削除の結合テスト（FS の 2.3、BR3.2〜BR3.4、AC1.1.3・AC1.1.4・AC1.1.13・AC4.1.14、計画の 8.3）。
 *
 * <p>割り当てが残るロールは数を返して断り、何も消さない。割り当てが無ければ、権限の設定とそのロールを指す作業ロールの保存を同じ
 * トランザクションで消してからロールを消し、ほかのロールを指す保存は残す。消した後の有効な作業ロールは最初のロールに読み替わる。
 */
@SpringBootTest
class RoleDeleteCleanupIT {

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.81", "Mozilla/5.0", "trace-0081");

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    RoleAdminService service;

    @Autowired
    EffectivePermissionResolver resolver;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private RoleFixtures roles;

    private GroupFixtures groups;

    private long actor;

    @BeforeEach
    void setUp() {
        roles = new RoleFixtures(jdbc);
        groups = new GroupFixtures(users, jdbc);
        actor = groups.user("削除 管理者");
    }

    private Map<String, Object> lastAudit(long roleId) {
        return jdbc.queryForMap(
                "SELECT event_type, result, failure_reason, detail FROM audit_events WHERE target_role_id = ?"
                        + " ORDER BY audit_event_id DESC LIMIT 1",
                roleId);
    }

    @Test
    @DisplayName("a role with direct and group assignments is refused with the counts and nothing is removed")
    void inUse() {
        long roleId = roles.role(RoleFixtures.uniqueName("使用中"));
        roles.setting(roleId, PermissionTarget.schema("SALES"), MainPermission.READ);
        long userA = groups.user("使用中 一郎");
        long userB = groups.user("使用中 二郎");
        roles.assignUser(roleId, userA);
        roles.assignUser(roleId, userB);
        roles.assignGroup(roleId, groups.group(GroupFixtures.uniqueName("使用中")));
        roles.selectWorkRole(userA, roleId);

        assertThat(service.delete(actor, ORIGIN, roleId)).isEqualTo(new RoleChangeResult.InUse(2, 1));

        assertThat(roles.roleRows(roleId)).isOne();
        assertThat(roles.settingRows(roleId)).isOne();
        assertThat(roles.assignmentRows(roleId)).isEqualTo(3);
        assertThat(roles.storedWorkRole(userA)).isEqualTo(roleId);
        assertThat(lastAudit(roleId))
                .containsEntry("EVENT_TYPE", "ROLE_DELETED")
                .containsEntry("RESULT", "FAILURE")
                .containsEntry("FAILURE_REASON", "ROLE_IN_USE");
        assertThat((String) lastAudit(roleId).get("DETAIL"))
                .contains("\"assignedUsers\":2")
                .contains("\"assignedGroups\":1");
    }

    @Test
    @DisplayName("a role without assignments is deleted with its settings and the selections pointing at it")
    void cleansUp() {
        long gone = roles.role(RoleFixtures.uniqueName("消す"));
        long kept = roles.role(RoleFixtures.uniqueName("残す"));
        roles.setting(gone, PermissionTarget.schema("SALES"), MainPermission.FULL);
        roles.setting(kept, PermissionTarget.schema("SALES"), MainPermission.READ);
        long remembering = groups.user("保存 消える");
        long other = groups.user("保存 残る");
        roles.assignUser(kept, remembering);
        roles.selectWorkRole(remembering, gone);
        roles.selectWorkRole(other, kept);

        assertThat(service.delete(actor, ORIGIN, gone)).isEqualTo(new RoleChangeResult.Done());

        assertThat(roles.roleRows(gone)).isZero();
        assertThat(roles.settingRows(gone)).isZero();
        assertThat(roles.storedWorkRole(remembering)).as("消したロールを指す保存は消える").isNull();
        assertThat(roles.storedWorkRole(other)).isEqualTo(kept);
        assertThat(roles.settingRows(kept)).isOne();
        assertThat(resolver.effectiveWorkRole(remembering).orElseThrow().roleId())
                .isEqualTo(kept);
        assertThat(lastAudit(gone)).containsEntry("EVENT_TYPE", "ROLE_DELETED").containsEntry("RESULT", "SUCCESS");
    }

    @Test
    @DisplayName("after the last assignment is removed the role can be deleted")
    void afterUnassigning() {
        long roleId = roles.role(RoleFixtures.uniqueName("外してから"));
        long userId = groups.user("外してから 一郎");
        roles.assignUser(roleId, userId);
        assertThat(service.delete(actor, ORIGIN, roleId)).isEqualTo(new RoleChangeResult.InUse(1, 0));

        jdbc.update("DELETE FROM user_role_assignments WHERE role_id = ?", roleId);

        assertThat(service.delete(actor, ORIGIN, roleId)).isEqualTo(new RoleChangeResult.Done());
        assertThat(roles.roleRows(roleId)).isZero();
    }
}
