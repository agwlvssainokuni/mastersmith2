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
package cherry.mastersmith.role.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** ロールと権限の設定の表の読み取り（BR3.5・BR3.7・BR4.4・BR4.7・BR4.10、NFR2.6）の結合テスト。 */
@SpringBootTest
class RoleRepositoryIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    RoleRepository roles;

    @Autowired
    PermissionSettingRepository settings;

    @Autowired
    JdbcTemplate jdbc;

    private RoleFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new RoleFixtures(jdbc);
    }

    private static Map<String, PermissionLevelRow> byName(List<PermissionLevelRow> rows) {
        return rows.stream().collect(Collectors.toMap(PermissionLevelRow::name, Function.identity()));
    }

    @Test
    @DisplayName("roles are listed by id, 20 per page, and found by id and by name key")
    void rolesByIdAndKey() {
        long before = roles.countAll();
        List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 21; i++) {
            ids.add(fixtures.role(RoleFixtures.uniqueName("一覧")));
        }
        String named = RoleFixtures.uniqueName("Sales");
        long namedId = fixtures.role(named);

        long total = roles.countAll();
        List<RoleRowView> all = roles.findPage(PageRequest.of(0, 1000));
        List<RoleRowView> page2 = roles.findPage(PageRequest.of(1, 20));

        assertThat(total).isEqualTo(before + 22);
        assertThat(all).extracting(RoleRowView::roleId).isSorted();
        assertThat(roles.findPage(PageRequest.of(0, 20))).hasSize(20);
        assertThat(page2)
                .first()
                .extracting(RoleRowView::roleId)
                .isEqualTo(all.get(20).roleId());
        assertThat(roles.findView(namedId))
                .hasValueSatisfying(view -> assertThat(view.name()).isEqualTo(named));
        assertThat(roles.findView(-1L)).isEmpty();
        assertThat(roles.existsRole(ids.getFirst())).isTrue();
        assertThat(roles.existsRole(0L)).isFalse();
        assertThat(roles.findIdByNameKey(RoleFixtures.name(named.toUpperCase()).key()))
                .contains(namedId);
    }

    @Test
    @DisplayName("the schema level reads each schema with its own values and the rows below, also only-deep schemas")
    void schemaLevel() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("1段目"));
        fixtures.setting(
                roleId, PermissionTarget.schema("SALES"), new PermissionValues(MainPermission.READ, true, null));
        fixtures.setting(roleId, PermissionTarget.table("SALES", "ORDER_LINE"), MainPermission.FULL);
        fixtures.setting(roleId, PermissionTarget.column("SALES", "ORDER_LINE", "QTY"), MainPermission.NONE);
        fixtures.setting(roleId, PermissionTarget.column("OLD", "T", "C"), MainPermission.READ);
        long other = fixtures.role(RoleFixtures.uniqueName("ほかのロール"));
        fixtures.setting(other, PermissionTarget.schema("HR"), MainPermission.FULL);

        Map<String, PermissionLevelRow> rows = byName(settings.findSchemaLevel(roleId));

        assertThat(rows).containsOnlyKeys("SALES", "OLD");
        assertThat(rows.get("SALES").values()).isEqualTo(new PermissionValues(MainPermission.READ, true, null));
        assertThat(rows.get("SALES").deeperRows()).isEqualTo(2);
        assertThat(rows.get("OLD").values()).isEqualTo(PermissionValues.NOT_SET);
        assertThat(rows.get("OLD").deeperRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("the table level reads each table of the schema, and the empty name is the schema row")
    void tableLevel() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("テーブル"));
        fixtures.setting(roleId, PermissionTarget.schema("SALES"), MainPermission.READ);
        fixtures.setting(roleId, PermissionTarget.table("SALES", "ORDER_LINE"), new PermissionValues(null, null, true));
        fixtures.setting(roleId, PermissionTarget.column("SALES", "ORDER_LINE", "QTY"), MainPermission.NONE);
        fixtures.setting(roleId, PermissionTarget.column("SALES", "GONE", "X"), MainPermission.FULL);
        fixtures.setting(roleId, PermissionTarget.table("HR", "EMPLOYEE"), MainPermission.FULL);

        Map<String, PermissionLevelRow> rows = byName(settings.findTableLevel(roleId, "SALES"));

        assertThat(rows).containsOnlyKeys("", "ORDER_LINE", "GONE");
        assertThat(rows.get("").values()).isEqualTo(new PermissionValues(MainPermission.READ, null, null));
        assertThat(rows.get("ORDER_LINE").values()).isEqualTo(new PermissionValues(null, null, true));
        assertThat(rows.get("ORDER_LINE").deeperRows()).isEqualTo(1);
        assertThat(rows.get("GONE").values().isEmpty()).isTrue();
        assertThat(rows.get("GONE").deeperRows()).isEqualTo(1);
        assertThat(settings.findTableLevel(roleId, "sales")).as("大文字と小文字を区別する").isEmpty();
    }

    @Test
    @DisplayName("the column level reads the schema row, the table row and the column rows of one table only")
    void columnLevel() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("カラム"));
        fixtures.setting(roleId, PermissionTarget.schema("SALES"), MainPermission.READ);
        fixtures.setting(roleId, PermissionTarget.table("SALES", "ORDER_LINE"), MainPermission.FULL);
        fixtures.setting(roleId, PermissionTarget.column("SALES", "ORDER_LINE", "QTY"), MainPermission.NONE);
        fixtures.setting(roleId, PermissionTarget.column("SALES", "ORDER_HEAD", "ID"), MainPermission.NONE);

        List<PermissionSettingRow> rows = settings.findColumnLevel(roleId, "SALES", "ORDER_LINE");

        assertThat(rows)
                .extracting(PermissionSettingRow::target)
                .containsExactlyInAnyOrder(
                        PermissionTarget.schema("SALES"),
                        PermissionTarget.table("SALES", "ORDER_LINE"),
                        PermissionTarget.column("SALES", "ORDER_LINE", "QTY"));
    }

    @Test
    @DisplayName("the scope read and the single row read return the current values of the targets")
    void scopeAndRow() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("範囲"));
        fixtures.setting(roleId, PermissionTarget.schema("SALES"), MainPermission.READ);
        fixtures.setting(roleId, PermissionTarget.table("SALES", "ORDER_LINE"), MainPermission.FULL);
        fixtures.setting(roleId, PermissionTarget.column("SALES", "ORDER_LINE", "QTY"), MainPermission.NONE);

        assertThat(settings.findInScope(roleId, "SALES", ""))
                .extracting(PermissionSettingRow::target)
                .containsExactly(PermissionTarget.schema("SALES"));
        assertThat(settings.findInScope(roleId, "SALES", "ORDER_LINE"))
                .extracting(PermissionSettingRow::target)
                .containsExactlyInAnyOrder(
                        PermissionTarget.table("SALES", "ORDER_LINE"),
                        PermissionTarget.column("SALES", "ORDER_LINE", "QTY"));
        assertThat(settings.findRow(roleId, "SALES", "ORDER_LINE", "QTY"))
                .hasValueSatisfying(row ->
                        assertThat(row.values()).isEqualTo(new PermissionValues(MainPermission.NONE, null, null)));
        assertThat(settings.findRow(roleId, "SALES", "ORDER_LINE", "NOPE")).isEmpty();
    }
}
