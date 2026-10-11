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
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.store.RoleStoreUnexpectedException;
import cherry.mastersmith.role.testsupport.FailingSettingTrigger;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
 * 権限の YAML の適用の一括確定の結合テスト（NFR3.2、BR9.11、AC3.1.8、{@code reliability-design.md} 2.5・4.3、計画の D-25）。
 *
 * <p>テストだけで内部DB に H2 のトリガー（{@link FailingSettingTrigger}）を置き、目印の対象の行の書き込み（JDBC のバッチの 1,000 件を
 * 超えた後）で失敗させる。適用の後に、すべてのロールと設定の行が前と同じで、{@code ROLE_TRANSFER_APPLIED} の成功の行が無いことを
 * 確かめる。JDBC のバッチと Hibernate の書き込み（作るロールの行）が同じトランザクションで巻き戻ることもここで確かめる。本番のコードは
 * 変えない。
 */
@SpringBootTest
class RoleTransferAtomicityIT {

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.70", "Mozilla/5.0", "trace-0070");

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    RoleTransferService service;

    @Autowired
    ActiveDslModelHolder holder;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private RoleFixtures roles;

    private long actor;

    @BeforeEach
    void setUp() {
        roles = new RoleFixtures(jdbc);
        actor = new GroupFixtures(users, jdbc).user("一括確定 確かめ一郎");
        RoleDslFixture.install(holder, RoleDslFixture.sample());
        FailingSettingTrigger.install(jdbc);
    }

    @AfterEach
    void tearDown() {
        FailingSettingTrigger.remove(jdbc);
    }

    private static RoleTransferBody body(String yaml) {
        return new RoleTransferBody(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    private List<Map<String, Object>> snapshot() {
        return jdbc.queryForList("SELECT r.role_id, r.name, r.updated_at, s.schema_name, s.table_name, s.column_name,"
                + " s.main_permission, s.create_permission, s.delete_permission FROM roles r LEFT JOIN permission_settings s"
                + " ON s.role_id = r.role_id ORDER BY r.role_id, s.schema_name, s.table_name, s.column_name");
    }

    private int auditRows(String result) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type = 'ROLE_TRANSFER_APPLIED' AND result = ?",
                Integer.class,
                result);
    }

    @Test
    @DisplayName(
            "a failure in the middle of the JDBC batch rolls back the created role and all settings without a success audit")
    void failureRollsBackEverything() {
        String replaced = RoleFixtures.uniqueName("置き換え");
        long replacedId = roles.role(replaced);
        roles.setting(replacedId, PermissionTarget.schema("SALES"), MainPermission.READ);
        roles.setting(replacedId, PermissionTarget.column("SALES", "ORDER_LINE", "QTY"), MainPermission.FULL);
        StringBuilder yaml = new StringBuilder("version: 1\nroles:\n");
        yaml.append("  \"").append(replaced).append("\":\n    schemas:\n      HR: {main: FULL}\n");
        yaml.append("  \"").append(RoleFixtures.uniqueName("作る")).append("\":\n    schemas:\n      SALES:\n");
        yaml.append("        tables:\n          T:\n            columns:\n");
        for (int i = 0; i < 1_500; i++) {
            yaml.append("              ")
                    .append(i == 1_200 ? FailingSettingTrigger.MARK : "C" + i)
                    .append(": READ\n");
        }
        String fingerprint = ((TransferCheckResult.Checked) service.check(body(yaml.toString())))
                .plan()
                .fingerprint();
        List<Map<String, Object>> before = snapshot();

        assertThatThrownBy(() -> service.apply(actor, ORIGIN, body(yaml.toString()), fingerprint))
                .isInstanceOf(RoleStoreUnexpectedException.class);

        assertThat(snapshot()).isEqualTo(before);
        assertThat(auditRows("SUCCESS")).isZero();
        assertThat(auditRows("FAILURE")).isZero();
        assertThat(service.check(body("version: 1\nroles: {}\n")))
                .as("例外で終わった後も入口は放されている")
                .isInstanceOf(TransferCheckResult.Checked.class);
    }

    @Test
    @DisplayName("without the failing row the same kind of apply commits once with one success audit")
    void successCommits() {
        String created = RoleFixtures.uniqueName("作る");
        String yaml =
                "version: 1\nroles:\n  \"" + created + "\":\n    schemas:\n      SALES: {main: READ, create: true}\n";
        String fingerprint =
                ((TransferCheckResult.Checked) service.check(body(yaml))).plan().fingerprint();

        assertThat(service.apply(actor, ORIGIN, body(yaml), fingerprint))
                .isEqualTo(new TransferApplyResult.Applied(1, 0));
        long createdId = jdbc.queryForObject("SELECT role_id FROM roles WHERE name = ?", Long.class, created);
        assertThat(roles.settingRows(createdId)).isEqualTo(1);
        assertThat(auditRows("SUCCESS")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_events WHERE event_type = 'ROLE_TRANSFER_APPLIED'"
                                + " AND target_role_id IS NULL AND target_user_id IS NULL AND target_group_id IS NULL"
                                + " AND detail LIKE ?",
                        Integer.class,
                        "%\"fingerprint\":\"" + fingerprint + "\"%"))
                .isEqualTo(1);
    }
}
