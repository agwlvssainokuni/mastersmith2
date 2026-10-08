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

import cherry.mastersmith.auth.testsupport.SqlStatementCounter;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionSnapshot;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.repository.UserRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
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
 * 解決の口の問い合わせの数（NFR2.3、{@code performance-design.md} 1節、計画の D-8・D-15・D-16）の結合テスト。
 *
 * <p>有効な作業ロールを決める読み取りは4回以内（直接の割り当て・所属のグループ・グループの割り当て・作業ロールの保存）、写しはそれに
 * 設定の行の1回、{@code resolve} はそれに祖先の行の1回で、割り当て・グループ・設定の行の数に比例しないことを、既存の
 * {@link SqlStatementCounter} で数える。
 */
@SpringBootTest(
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "cherry.mastersmith.auth.testsupport.SqlStatementCounter")
class EffectivePermissionQueryCountIT {

    private static final List<String> DECIDE = List.of(
            "select user_role_assignments",
            "select group_members",
            "select group_role_assignments",
            "select work_role_selections");

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

    private static List<String> statementsOf(Supplier<?> call) {
        SqlStatementCounter.start();
        call.get();
        Map<String, List<String>> statements = SqlStatementCounter.stop();
        return statements.values().stream().flatMap(List::stream).toList();
    }

    private static List<String> plus(List<String> base, String last) {
        List<String> all = new ArrayList<>(base);
        all.add(last);
        return all;
    }

    /** 利用者を作り、直接の割り当てを n 個、所属するグループ（それぞれロールを割り当てた）を m 個持たせる。作業ロールは最初の直接のロール。 */
    private long userWith(int direct, int viaGroups, int settingRows) {
        long userId = groups.user("数え 利用者");
        long workRole = 0;
        for (int i = 0; i < direct; i++) {
            long roleId = roles.role(RoleFixtures.uniqueName("直接"));
            roles.assignUser(roleId, userId);
            workRole = i == 0 ? roleId : workRole;
        }
        for (int i = 0; i < viaGroups; i++) {
            long groupId = groups.group(GroupFixtures.uniqueName("所属"));
            groups.member(groupId, userId, Instant.parse("2026-10-02T00:00:00Z"));
            roles.assignGroup(roles.role(RoleFixtures.uniqueName("グループ")), groupId);
        }
        roles.selectWorkRole(userId, workRole);
        roles.setting(workRole, PermissionTarget.schema("SALES"), MainPermission.READ);
        OffsetDateTime at = OffsetDateTime.ofInstant(Instant.parse("2026-10-02T00:00:00Z"), ZoneOffset.UTC);
        List<Object[]> rows = new ArrayList<>();
        for (int i = 1; i < settingRows; i++) {
            rows.add(new Object[] {workRole, "OLD", "T" + (i / 100), "C" + i, "NONE", at});
        }
        jdbc.batchUpdate(
                "INSERT INTO permission_settings (role_id, schema_name, table_name, column_name, main_permission,"
                        + " updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                rows);
        return userId;
    }

    @Test
    @DisplayName("deciding the work role reads four times for 1 and for 100 direct assignments and groups")
    void decideStatements() {
        long small = userWith(1, 1, 1);
        long large = userWith(100, 100, 1);

        assertThat(statementsOf(() -> resolver.effectiveWorkRole(small))).isEqualTo(DECIDE);
        assertThat(statementsOf(() -> resolver.effectiveWorkRole(large))).isEqualTo(DECIDE);
    }

    @Test
    @DisplayName("the snapshot adds one read of the settings for 1 and for 10,000 setting rows")
    void snapshotStatements() {
        long small = userWith(1, 1, 1);
        long large = userWith(2, 2, 10_000);

        List<String> expected = plus(DECIDE, "select permission_settings");
        assertThat(statementsOf(() -> resolver.snapshotFor(small))).isEqualTo(expected);
        List<String> largeStatements = new ArrayList<>();
        PermissionSnapshot[] snapshot = new PermissionSnapshot[1];
        largeStatements.addAll(statementsOf(() -> snapshot[0] = resolver.snapshotFor(large)));
        assertThat(largeStatements).isEqualTo(expected);
        assertThat(snapshot[0].settingCount()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("resolve adds one read of the ancestors of the target for 1 and for 10,000 setting rows")
    void resolveStatements() {
        long small = userWith(1, 1, 1);
        long large = userWith(2, 2, 10_000);
        PermissionTarget target = PermissionTarget.column("SALES", "ORDER_LINE", "QTY");

        List<String> expected = plus(DECIDE, "select permission_settings");
        assertThat(statementsOf(() -> resolver.resolve(small, target))).isEqualTo(expected);
        assertThat(statementsOf(() -> resolver.resolve(large, target))).isEqualTo(expected);
        assertThat(resolver.resolve(large, target).main()).isEqualTo(MainPermission.READ);
    }

    @Test
    @DisplayName("a user without groups skips the read of the group assignments")
    void withoutGroups() {
        long userId = userWith(1, 0, 1);

        assertThat(statementsOf(() -> resolver.effectiveWorkRole(userId)))
                .containsExactly("select user_role_assignments", "select group_members", "select work_role_selections");
    }
}
