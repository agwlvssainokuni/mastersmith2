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
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.role.domain.EffectivePermission;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionSnapshot;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.repository.UserRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
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
 * 写しと {@code resolve} の一致の結合テスト（{@code reliability-design.md} 3節の8つの組、BR5.1〜BR5.5・BR7.2、navigation の R-01・R-08、
 * 計画の 8.3）。
 *
 * <p>期待はテストの用意（DSL・明示の設定・割り当て）から、テストの中に書いた期待の表（対象ごとの主権限・CREATE・DELETE）で持つ。表に
 * 書かない対象は NONE・不可。すべての対象で、{@code resolve} の値と写しの値がどちらも期待の表に等しいことを確かめる。
 */
@SpringBootTest
class EffectivePermissionConsistencyIT {

    /** 小さな DSL のすべての対象（スキーマ・テーブル・カラム）。 */
    private static final List<PermissionTarget> DSL_TARGETS = List.of(
            PermissionTarget.schema("SALES"),
            PermissionTarget.table("SALES", "ORDER_LINE"),
            PermissionTarget.column("SALES", "ORDER_LINE", "ORDER_NO"),
            PermissionTarget.column("SALES", "ORDER_LINE", "UNIT_PRICE"),
            PermissionTarget.column("SALES", "ORDER_LINE", "QTY"),
            PermissionTarget.table("SALES", "ORDER_HEAD"),
            PermissionTarget.column("SALES", "ORDER_HEAD", "ID"),
            PermissionTarget.schema("HR"),
            PermissionTarget.table("HR", "EMPLOYEE"),
            PermissionTarget.column("HR", "EMPLOYEE", "ID"),
            PermissionTarget.column("HR", "EMPLOYEE", "NAME"));

    /** 今の DSL に無い対象（設定は名前で残るが、解決では NONE・不可）。 */
    private static final List<PermissionTarget> OUTSIDE_TARGETS = List.of(
            PermissionTarget.schema("GONE"),
            PermissionTarget.table("SALES", "NO_TABLE"),
            PermissionTarget.column("SALES", "ORDER_LINE", "NOT_A_COLUMN"),
            PermissionTarget.table("sales", "ORDER_LINE"));

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    EffectivePermissionResolver resolver;

    @Autowired
    ActiveDslModelHolder holder;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private RoleFixtures roles;

    private GroupFixtures groups;

    @BeforeEach
    void setUp() {
        roles = new RoleFixtures(jdbc);
        groups = new GroupFixtures(users, jdbc);
        RoleDslFixture.install(holder, RoleDslFixture.sample());
    }

    @AfterEach
    void tearDown() {
        RoleDslFixture.install(holder, RoleDslFixture.sample());
    }

    private static EffectivePermission p(MainPermission main, boolean create, boolean delete) {
        return new EffectivePermission(main, create, delete);
    }

    /** すべての対象（DSL の中と外）で、resolve と写しが期待の表に等しいことを確かめる。 */
    private void assertConsistent(long userId, Map<PermissionTarget, EffectivePermission> expected, Long workRoleId) {
        PermissionSnapshot snapshot = resolver.snapshotFor(userId);
        assertThat(snapshot.workRole().map(role -> role.roleId()).orElse(null)).isEqualTo(workRoleId);
        assertThat(resolver.effectiveWorkRole(userId).map(role -> role.roleId()).orElse(null))
                .isEqualTo(workRoleId);
        for (PermissionTarget target : DSL_TARGETS) {
            EffectivePermission want = expected.getOrDefault(target, EffectivePermission.NONE);
            assertThat(resolver.resolve(userId, target))
                    .as("resolve %s", target)
                    .isEqualTo(want);
            assertThat(snapshot.effective(target)).as("snapshot %s", target).isEqualTo(want);
        }
        for (PermissionTarget target : OUTSIDE_TARGETS) {
            assertThat(resolver.resolve(userId, target))
                    .as("resolve %s", target)
                    .isEqualTo(EffectivePermission.NONE);
            assertThat(snapshot.effective(target)).as("snapshot %s", target).isEqualTo(EffectivePermission.NONE);
        }
    }

    /** ロールを作り、利用者に直接割り当て、作業ロールに選ぶ。 */
    private long workRoleOf(long userId) {
        long roleId = roles.role(RoleFixtures.uniqueName("一致"));
        roles.assignUser(roleId, userId);
        roles.selectWorkRole(userId, roleId);
        return roleId;
    }

    @Test
    @DisplayName("(a) a lower explicit value overrides the upper one in both directions")
    void lowerOverridesUpper() {
        long userId = groups.user("一致 甲");
        long roleId = workRoleOf(userId);
        roles.setting(roleId, PermissionTarget.schema("SALES"), new PermissionValues(MainPermission.NONE, true, null));
        roles.setting(roleId, PermissionTarget.table("SALES", "ORDER_LINE"), MainPermission.READ);
        roles.setting(roleId, PermissionTarget.schema("HR"), new PermissionValues(MainPermission.FULL, true, true));
        roles.setting(
                roleId,
                PermissionTarget.table("HR", "EMPLOYEE"),
                new PermissionValues(MainPermission.NONE, null, false));

        Map<PermissionTarget, EffectivePermission> expected = new LinkedHashMap<>();
        expected.put(PermissionTarget.schema("SALES"), p(MainPermission.NONE, true, false));
        expected.put(PermissionTarget.table("SALES", "ORDER_LINE"), p(MainPermission.READ, true, false));
        expected.put(PermissionTarget.column("SALES", "ORDER_LINE", "ORDER_NO"), p(MainPermission.READ, true, false));
        expected.put(PermissionTarget.column("SALES", "ORDER_LINE", "UNIT_PRICE"), p(MainPermission.READ, true, false));
        expected.put(PermissionTarget.column("SALES", "ORDER_LINE", "QTY"), p(MainPermission.READ, true, false));
        expected.put(PermissionTarget.table("SALES", "ORDER_HEAD"), p(MainPermission.NONE, true, false));
        expected.put(PermissionTarget.column("SALES", "ORDER_HEAD", "ID"), p(MainPermission.NONE, true, false));
        expected.put(PermissionTarget.schema("HR"), p(MainPermission.FULL, true, true));
        expected.put(PermissionTarget.table("HR", "EMPLOYEE"), p(MainPermission.NONE, true, false));
        expected.put(PermissionTarget.column("HR", "EMPLOYEE", "ID"), p(MainPermission.NONE, true, false));
        expected.put(PermissionTarget.column("HR", "EMPLOYEE", "NAME"), p(MainPermission.NONE, true, false));
        assertConsistent(userId, expected, roleId);
    }

    @Test
    @DisplayName("(b) an explicit value only on a column affects only that column")
    void columnOnly() {
        long userId = groups.user("一致 乙");
        long roleId = workRoleOf(userId);
        roles.setting(roleId, PermissionTarget.column("SALES", "ORDER_LINE", "QTY"), MainPermission.FULL);

        assertConsistent(
                userId,
                Map.of(PermissionTarget.column("SALES", "ORDER_LINE", "QTY"), p(MainPermission.FULL, false, false)),
                roleId);
    }

    @Test
    @DisplayName("(c) a work role without settings and (d) a user without roles are NONE everywhere")
    void notSetAndNoWorkRole() {
        long withRole = groups.user("一致 丙");
        long roleId = workRoleOf(withRole);
        long withoutRole = groups.user("一致 丁");

        assertConsistent(withRole, Map.of(), roleId);
        assertConsistent(withoutRole, Map.of(), null);
    }

    @Test
    @DisplayName("(e) without an applied DSL everything is NONE, and the settings come back with the DSL")
    void noDsl() {
        long userId = groups.user("一致 戊");
        long roleId = workRoleOf(userId);
        roles.setting(roleId, PermissionTarget.schema("HR"), MainPermission.FULL);

        RoleDslFixture.remove(holder);
        assertConsistent(userId, Map.of(), roleId);
        assertThat(resolver.snapshotFor(userId).dslHash()).isEmpty();

        RoleDslFixture.install(holder, RoleDslFixture.sample());
        assertThat(resolver.resolve(userId, PermissionTarget.column("HR", "EMPLOYEE", "NAME"))
                        .main())
                .isEqualTo(MainPermission.FULL);
    }

    @Test
    @DisplayName("(f) settings on names outside the current DSL are kept but not used")
    void namesOutsideTheDsl() {
        long userId = groups.user("一致 己");
        long roleId = workRoleOf(userId);
        roles.setting(roleId, PermissionTarget.schema("GONE"), new PermissionValues(MainPermission.FULL, true, true));
        roles.setting(roleId, PermissionTarget.table("SALES", "NO_TABLE"), MainPermission.FULL);
        roles.setting(roleId, PermissionTarget.column("SALES", "ORDER_LINE", "NOT_A_COLUMN"), MainPermission.FULL);
        roles.setting(roleId, PermissionTarget.schema("SALES"), MainPermission.READ);

        Map<PermissionTarget, EffectivePermission> expected = new LinkedHashMap<>();
        for (PermissionTarget target : DSL_TARGETS) {
            if (target.schemaName().equals("SALES")) {
                expected.put(target, p(MainPermission.READ, false, false));
            }
        }
        assertConsistent(userId, expected, roleId);
    }

    @Test
    @DisplayName("(g) a work role reached through a group is used alone, without the direct role's settings")
    void directAndGroupRoles() {
        long userId = groups.user("一致 庚");
        long direct = roles.role(RoleFixtures.uniqueName("一致 直接"));
        long viaGroup = roles.role(RoleFixtures.uniqueName("一致 グループ"));
        long groupId = groups.group(GroupFixtures.uniqueName("一致のグループ"));
        roles.assignUser(direct, userId);
        groups.member(groupId, userId, Instant.parse("2026-10-02T00:00:00Z"));
        roles.assignGroup(viaGroup, groupId);
        roles.selectWorkRole(userId, viaGroup);
        roles.setting(direct, PermissionTarget.schema("SALES"), MainPermission.FULL);
        roles.setting(viaGroup, PermissionTarget.schema("HR"), MainPermission.READ);

        Map<PermissionTarget, EffectivePermission> expected = new LinkedHashMap<>();
        for (PermissionTarget target : DSL_TARGETS) {
            if (target.schemaName().equals("HR")) {
                expected.put(target, p(MainPermission.READ, false, false));
            }
        }
        assertConsistent(userId, expected, viaGroup);
    }

    @Test
    @DisplayName("(h) a stored role outside the assignments is read as the first role")
    void storedOutsideTheAssignments() {
        long userId = groups.user("一致 辛");
        long first = roles.role(RoleFixtures.uniqueName("一致 最初"));
        long second = roles.role(RoleFixtures.uniqueName("一致 次"));
        long notAssigned = roles.role(RoleFixtures.uniqueName("一致 割り当て外"));
        roles.assignUser(second, userId);
        roles.assignUser(first, userId);
        roles.selectWorkRole(userId, notAssigned);
        roles.setting(first, PermissionTarget.table("SALES", "ORDER_HEAD"), MainPermission.READ);
        roles.setting(second, PermissionTarget.schema("SALES"), MainPermission.FULL);
        roles.setting(notAssigned, PermissionTarget.schema("HR"), MainPermission.FULL);

        assertConsistent(
                userId,
                Map.of(
                        PermissionTarget.table("SALES", "ORDER_HEAD"), p(MainPermission.READ, false, false),
                        PermissionTarget.column("SALES", "ORDER_HEAD", "ID"), p(MainPermission.READ, false, false)),
                first);
        assertThat(roles.storedWorkRole(userId)).as("読み替えは保存を書き換えない").isEqualTo(notAssigned);
    }
}
